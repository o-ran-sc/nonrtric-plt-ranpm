// -
//
//	========================LICENSE_START=================================
//	O-RAN-SC
//	%%
//	Copyright (C) 2023: Nordix Foundation
//	%%
//	Licensed under the Apache License, Version 2.0 (the "License");
//	you may not use this file except in compliance with the License.
//	You may obtain a copy of the License at
//
//	     http://www.apache.org/licenses/LICENSE-2.0
//
//	Unless required by applicable law or agreed to in writing, software
//	distributed under the License is distributed on an "AS IS" BASIS,
//	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//	See the License for the specific language governing permissions and
//	limitations under the License.
//	========================LICENSE_END===================================
package main

import (
	"fmt"
	jsoniter "github.com/json-iterator/go"
	log "github.com/sirupsen/logrus"
	"main/common/dataTypes"
	"main/common/utils"
	"main/components/kafkacollector"
	"net/http"
	"os"
	"os/signal"
	"runtime"
	"sync"
	"syscall"
	"time"
)

var ics_server = os.Getenv("ICS")
var self = os.Getenv("SELF")

// This are optional - set if using SASL protocol is used towards kafka
var creds_grant_type = os.Getenv("CREDS_GRANT_TYPE")

var bootstrapserver = os.Getenv("KAFKA_SERVER")

const config_file = "application_configuration.json"
const producer_name = "kafka-producer"

var producer_instance_name string = producer_name

const reader_queue_length = 100 //Per type job
const writer_queue_length = 100 //Per info job

var files_volume = os.Getenv("FILES_VOLUME")

var data_out_channel = make(chan *dataTypes.KafkaPayload, writer_queue_length)
var writer_control = make(chan dataTypes.WriterControl, 1)

const registration_delay_short = 2
const registration_delay_long = 120

//== Variables ==//

var AppState = Init

// Lock for all internal data
var datalock sync.Mutex

const (
	Init dataTypes.AppStates = iota
	Running
	Terminating
)

// == Main ==//
func main() {

	//log.SetLevel(log.InfoLevel)
	log.SetLevel(log.TraceLevel)

	log.Info("Server starting...")

	if self == "" {
		log.Panic("Env SELF not configured")
	}
	if bootstrapserver == "" {
		log.Panic("Env KAFKA_SERVER not set")
	}
	if ics_server == "" {
		log.Panic("Env ICS not set")
	}
	if os.Getenv("KP") != "" {
		producer_instance_name = producer_instance_name + "-" + os.Getenv("KP")
	}

	go kafkacollector.Start_topic_writer(writer_control, data_out_channel)

	//Setup proc for periodic type registration
	var event_chan = make(chan int) //Channel for stopping the proc
	go periodic_registration(event_chan)

	//Wait for term/int signal do try to shut down gracefully
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigs
		fmt.Printf("Received signal %s - application will terminate\n", sig)
		event_chan <- 0 // Stop periodic registration
		datalock.Lock()
		defer datalock.Unlock()
		AppState = Terminating
	}()

	AppState = Running

	//Wait until all go routines has exited
	runtime.Goexit()

	fmt.Println("main routine exit")
	fmt.Println("server stopped")
}

// == Core functions ==//
// Run periodic registration of producers
func periodic_registration(evtch chan int) {
	var delay int = 1
	for {
		select {
		case msg := <-evtch:
			if msg == 0 { // Stop thread
				return
			}
		case <-time.After(time.Duration(delay) * time.Second):
			ok := register_producer()
			if ok {
				delay = registration_delay_long
			} else {
				if delay < registration_delay_long {
					delay += registration_delay_short
				} else {
					delay = registration_delay_short
				}
			}
		}
	}
}

func register_producer() bool {

	log.Info("Registering producer: ", producer_instance_name)

	file, err := os.ReadFile(config_file)
	if err != nil {
		log.Error("Cannot read config file: ", config_file)
		log.Error("Registering producer: ", producer_instance_name, " - failed")
		return false
	}
	data := dataTypes.DataTypes{}
	err = jsoniter.Unmarshal([]byte(file), &data)
	if err != nil {
		log.Error("Cannot parse config file: ", config_file)
		log.Error("Registering producer: ", producer_instance_name, " - failed")
		return false
	}
	var new_type_names []string

	for i := 0; i < len(data.ProdDataTypes); i++ {
		t1 := make(map[string]interface{})
		t2 := make(map[string]interface{})

		t2["schema"] = "http://json-schema.org/draft-07/schema#"
		t2["title"] = data.ProdDataTypes[i].ID
		t2["description"] = data.ProdDataTypes[i].ID
		t2["type"] = "object"

		t1["info_job_data_schema"] = t2

		json, err := jsoniter.Marshal(t1)
		if err != nil {
			log.Error("Cannot create json for type: ", data.ProdDataTypes[i].ID)
			log.Error("Registering producer: ", producer_instance_name, " - failed")
			return false
		} else {
			ok := utils.Send_http_request(json, http.MethodPut, "http://"+ics_server+"/data-producer/v1/info-types/"+data.ProdDataTypes[i].ID, true, creds_grant_type != "")
			if !ok {
				log.Error("Cannot register type: ", data.ProdDataTypes[i].ID)
				log.Error("Registering producer: ", producer_instance_name, " - failed")
				return false
			}
			new_type_names = append(new_type_names, data.ProdDataTypes[i].ID)
		}

	}

	log.Debug("Registering types: ", new_type_names)
	datalock.Lock()
	defer datalock.Unlock()

	for _, v := range data.ProdDataTypes {
		log.Info("Adding type job for type: ", v.ID, " Type added to configuration")
		start_type_job(v)
	}

	dataTypes.InfoTypes = data
	log.Debug("Datatypes: ", dataTypes.InfoTypes)
	log.Info("Registering producer: ", producer_instance_name, " - OK")
	return true
}

func start_type_job(dp dataTypes.DataType) {
	log.Info("Starting type job: ", dp.ID)
	job_record := dataTypes.TypeJobRecord{}

	job_record.Job_control = make(chan dataTypes.JobControl, 1)
	job_record.Reader_control = make(chan dataTypes.ReaderControl, 1)
	job_record.Data_in_channel = make(chan *dataTypes.KafkaPayload, reader_queue_length)
	job_record.InfoType = dp.ID
	job_record.InputTopic = dp.KafkaInputTopic
	job_record.GroupId = "kafka-procon-" + dp.ID
	job_record.ClientId = dp.ID + "-" + os.Getenv("KP")

	switch dp.ID {
	case "xml-file-data-to-filestore":
		go kafkacollector.Start_job_xml_file_data(dp.ID, job_record.Job_control, job_record.Data_in_channel, data_out_channel, "", "pm-files-json")
	case "xml-file-data":
		go kafkacollector.Start_job_xml_file_data(dp.ID, job_record.Job_control, job_record.Data_in_channel, data_out_channel, files_volume, "")
	default:
	}

	go kafkacollector.Start_topic_reader(dp.KafkaInputTopic, dp.ID, job_record.Reader_control, job_record.Data_in_channel, job_record.GroupId, job_record.ClientId)

	dataTypes.TypeJobs[dp.ID] = job_record
	log.Debug("Type job input type: ", dp.InputJobType)
}
