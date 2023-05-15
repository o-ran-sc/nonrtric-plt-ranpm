//  ============LICENSE_START===============================================
//  Copyright (C) 2023 Nordix Foundation. All rights reserved.
//  ========================================================================
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//  ============LICENSE_END=================================================
//

package main

import (
	"bytes"
	"compress/gzip"
	"context"
	"crypto/tls"
	"encoding/json"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"net"
	"os/signal"
	"reflect"
	"strings"
	"sync"
	"syscall"

	"net/http"
	"os"
	"runtime"
	"strconv"
	"time"

	"github.com/google/uuid"
	"golang.org/x/oauth2/clientcredentials"

	log "github.com/sirupsen/logrus"

	"github.com/gorilla/mux"

	"net/http/pprof"

	"github.com/confluentinc/confluent-kafka-go/kafka"
	influxdb2 "github.com/influxdata/influxdb-client-go/v2"
	jsoniter "github.com/json-iterator/go"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

//== Constants ==//

const http_port = 80
const https_port = 443
const config_file = "application_configuration.json"
const server_crt = "server.crt"
const server_key = "server.key"

const producer_name = "kafka-producer"

const registration_delay_short = 2
const registration_delay_long = 120

const mutexLocked = 1

const (
	Init AppStates = iota
	Running
	Terminating
)

const reader_queue_length = 100 //Per type job
const writer_queue_length = 100 //Per info job
const parallelism_limiter = 100 //For all jobs

// This are optional - set if using SASL protocol is used towards kafka
var creds_grant_type = os.Getenv("CREDS_GRANT_TYPE")
var creds_client_secret = os.Getenv("CREDS_CLIENT_SECRET")
var creds_client_id = os.Getenv("CREDS_CLIENT_ID")
var creds_service_url = os.Getenv("AUTH_SERVICE_URL")

//== Types ==//

type AppStates int64

type FilterParameters struct {
	MeasuredEntityDns []string `json:"measuredEntityDns"`
	MeasTypes         []string `json:"measTypes"`
	MeasObjClass      []string `json:"measObjClass"`
	MeasObjInstIds    []string `json:"measObjInstIds"`
}

type InfoJobDataType struct {
	InfoJobData struct {
		KafkaOutputTopic string `json:"kafkaOutputTopic"`

		DbUrl    string `json:"db-url"`
		DbOrg    string `json:"db-org"`
		DbBucket string `json:"db-bucket"`
		DbToken  string `json:"db-token"`

		FilterParams FilterParameters `json:"filter"`
	} `json:"info_job_data"`
	InfoJobIdentity  string `json:"info_job_identity"`
	InfoTypeIdentity string `json:"info_type_identity"`
	LastUpdated      string `json:"last_updated"`
	Owner            string `json:"owner"`
	TargetURI        string `json:"target_uri"`
}

// Type for an infojob
type InfoJobRecord struct {
	job_info     InfoJobDataType
	output_topic string

	statistics *InfoJobStats
}

// Type for an infojob
type TypeJobRecord struct {
	InfoType        string
	InputTopic      string
	data_in_channel chan *KafkaPayload
	reader_control  chan ReaderControl
	job_control     chan JobControl
	groupId         string
	clientId        string

	statistics *TypeJobStats
}

// Type for controlling the topic reader
type ReaderControl struct {
	command string
}

// Type for controlling the topic writer
type WriterControl struct {
	command string
}

// Type for controlling the job
type JobControl struct {
	command string
	filter  Filter
}

type KafkaPayload struct {
	msg   *kafka.Message
	topic string
	jobid string
}

type FilterMaps struct {
	sourceNameMap     map[string]bool
	measObjClassMap   map[string]bool
	measObjInstIdsMap map[string]bool
	measTypesMap      map[string]bool
}

type InfluxJobParameters struct {
	DbUrl    string
	DbOrg    string
	DbBucket string
	DbToken  string
}

type Filter struct {
	JobId       string
	OutputTopic string
	filter      FilterMaps

	influxParameters InfluxJobParameters
}

// Type for info job statistics
type InfoJobStats struct {
	out_msg_cnt  int
	out_data_vol int64
}

// Type for type job statistics
type TypeJobStats struct {
	in_msg_cnt  int
	in_data_vol int64
}

// == API Datatypes ==//
// Type for supported data types
type DataType struct {
	ID                 string `json:"id"`
	KafkaInputTopic    string `json:"kafkaInputTopic"`
	InputJobType       string `json:inputJobType`
	InputJobDefinition struct {
		KafkaOutputTopic string `json:kafkaOutputTopic`
	} `json:inputJobDefinition`

	ext_job         *[]byte
	ext_job_created bool
	ext_job_id      string
}

type DataTypes struct {
	ProdDataTypes []DataType `json:"types"`
}

type Minio_buckets struct {
	Buckets map[string]bool
}

//== External data types ==//

// // Data type for event xml file download
type XmlFileEventHeader struct {
	ProductName        string `json:"productName"`
	VendorName         string `json:"vendorName"`
	Location           string `json:"location"`
	Compression        string `json:"compression"`
	SourceName         string `json:"sourceName"`
	FileFormatType     string `json:"fileFormatType"`
	FileFormatVersion  string `json:"fileFormatVersion"`
	StartEpochMicrosec int64  `json:"startEpochMicrosec"`
	LastEpochMicrosec  int64  `json:"lastEpochMicrosec"`
	Name               string `json:"name"`
	ChangeIdentifier   string `json:"changeIdentifier"`
	InternalLocation   string `json:"internalLocation"`
	TimeZoneOffset     string `json:"timeZoneOffset"`
	//ObjectStoreBucket  string `json:"objectStoreBucket"`
}

// Data types for input xml file
type MeasCollecFile struct {
	XMLName        xml.Name `xml:"measCollecFile"`
	Text           string   `xml:",chardata"`
	Xmlns          string   `xml:"xmlns,attr"`
	Xsi            string   `xml:"xsi,attr"`
	SchemaLocation string   `xml:"schemaLocation,attr"`
	FileHeader     struct {
		Text              string `xml:",chardata"`
		FileFormatVersion string `xml:"fileFormatVersion,attr"`
		VendorName        string `xml:"vendorName,attr"`
		DnPrefix          string `xml:"dnPrefix,attr"`
		FileSender        struct {
			Text        string `xml:",chardata"`
			LocalDn     string `xml:"localDn,attr"`
			ElementType string `xml:"elementType,attr"`
		} `xml:"fileSender"`
		MeasCollec struct {
			Text      string `xml:",chardata"`
			BeginTime string `xml:"beginTime,attr"`
		} `xml:"measCollec"`
	} `xml:"fileHeader"`
	MeasData struct {
		Text           string `xml:",chardata"`
		ManagedElement struct {
			Text      string `xml:",chardata"`
			LocalDn   string `xml:"localDn,attr"`
			SwVersion string `xml:"swVersion,attr"`
		} `xml:"managedElement"`
		MeasInfo []struct {
			Text       string `xml:",chardata"`
			MeasInfoId string `xml:"measInfoId,attr"`
			Job        struct {
				Text  string `xml:",chardata"`
				JobId string `xml:"jobId,attr"`
			} `xml:"job"`
			GranPeriod struct {
				Text     string `xml:",chardata"`
				Duration string `xml:"duration,attr"`
				EndTime  string `xml:"endTime,attr"`
			} `xml:"granPeriod"`
			RepPeriod struct {
				Text     string `xml:",chardata"`
				Duration string `xml:"duration,attr"`
			} `xml:"repPeriod"`
			MeasType []struct {
				Text string `xml:",chardata"`
				P    string `xml:"p,attr"`
			} `xml:"measType"`
			MeasValue []struct {
				Text       string `xml:",chardata"`
				MeasObjLdn string `xml:"measObjLdn,attr"`
				R          []struct {
					Text string `xml:",chardata"`
					P    string `xml:"p,attr"`
				} `xml:"r"`
				Suspect string `xml:"suspect"`
			} `xml:"measValue"`
		} `xml:"measInfo"`
	} `xml:"measData"`
	FileFooter struct {
		Text       string `xml:",chardata"`
		MeasCollec struct {
			Text    string `xml:",chardata"`
			EndTime string `xml:"endTime,attr"`
		} `xml:"measCollec"`
	} `xml:"fileFooter"`
}

// Data type for json file
// Splitted in sevreal part to allow add/remove in lists
type MeasResults struct {
	P      int    `json:"p"`
	SValue string `json:"sValue"`
}

type MeasValues struct {
	MeasObjInstID   string        `json:"measObjInstId"`
	SuspectFlag     string        `json:"suspectFlag"`
	MeasResultsList []MeasResults `json:"measResults"`
}

type SMeasTypes struct {
	SMeasType string `json:"sMeasTypesList"`
}

type MeasInfoList struct {
	MeasInfoID struct {
		SMeasInfoID string `json:"sMeasInfoId"`
	} `json:"measInfoId"`
	MeasTypes struct {
		SMeasTypesList []string `json:"sMeasTypesList"`
	} `json:"measTypes"`
	MeasValuesList []MeasValues `json:"measValuesList"`
}

type PMJsonFile struct {
	Event struct {
		CommonEventHeader struct {
			Domain                  string `json:"domain"`
			EventID                 string `json:"eventId"`
			Sequence                int    `json:"sequence"`
			EventName               string `json:"eventName"`
			SourceName              string `json:"sourceName"`
			ReportingEntityName     string `json:"reportingEntityName"`
			Priority                string `json:"priority"`
			StartEpochMicrosec      int64  `json:"startEpochMicrosec"`
			LastEpochMicrosec       int64  `json:"lastEpochMicrosec"`
			Version                 string `json:"version"`
			VesEventListenerVersion string `json:"vesEventListenerVersion"`
			TimeZoneOffset          string `json:"timeZoneOffset"`
		} `json:"commonEventHeader"`
		Perf3GppFields struct {
			Perf3GppFieldsVersion string `json:"perf3gppFieldsVersion"`
			MeasDataCollection    struct {
				GranularityPeriod             int            `json:"granularityPeriod"`
				MeasuredEntityUserName        string         `json:"measuredEntityUserName"`
				MeasuredEntityDn              string         `json:"measuredEntityDn"`
				MeasuredEntitySoftwareVersion string         `json:"measuredEntitySoftwareVersion"`
				SMeasInfoList                 []MeasInfoList `json:"measInfoList"`
			} `json:"measDataCollection"`
		} `json:"perf3gppFields"`
	} `json:"event"`
}

// Data type for converted json file message
type FileDownloadedEvt struct {
	Filename string `json:"filename"`
}

//== Variables ==//

var AppState = Init

// Lock for all internal data
var datalock sync.Mutex

var producer_instance_name string = producer_name

// Keep all info type jobs, key == type id
var TypeJobs map[string]TypeJobRecord = make(map[string]TypeJobRecord)

// Keep all info jobs, key == job id
var InfoJobs map[string]InfoJobRecord = make(map[string]InfoJobRecord)

var InfoTypes DataTypes

// Limiter - valid for all jobs
var jobLimiterChan = make(chan struct{}, parallelism_limiter)

// TODO: Config param?
var bucket_location = "swe"

var httpclient = &http.Client{}

// == Env variables ==//
var bootstrapserver = os.Getenv("KAFKA_SERVER")
var files_volume = os.Getenv("FILES_VOLUME")
var ics_server = os.Getenv("ICS")
var self = os.Getenv("SELF")
var filestore_user = os.Getenv("FILESTORE_USER")
var filestore_pwd = os.Getenv("FILESTORE_PWD")
var filestore_server = os.Getenv("FILESTORE_SERVER")

var data_out_channel = make(chan *KafkaPayload, writer_queue_length)
var writer_control = make(chan WriterControl, 1)

var minio_bucketlist map[string]Minio_buckets = make(map[string]Minio_buckets)

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

	rtr := mux.NewRouter()
	rtr.HandleFunc("/callbacks/job/"+producer_instance_name, create_job)
	rtr.HandleFunc("/callbacks/job/"+producer_instance_name+"/{job_id}", delete_job)
	rtr.HandleFunc("/callbacks/supervision/"+producer_instance_name, supervise_producer)
	rtr.HandleFunc("/statistics", statistics)
	rtr.HandleFunc("/logging/{level}", logging_level)
	rtr.HandleFunc("/logging", logging_level)
	rtr.HandleFunc("/", alive)

	//For perf/mem profiling
	rtr.HandleFunc("/custom_debug_path/profile", pprof.Profile)

	http.Handle("/", rtr)

	http_server := &http.Server{Addr: ":" + strconv.Itoa(http_port), Handler: nil}

	cer, err := tls.LoadX509KeyPair(server_crt, server_key)
	if err != nil {
		log.Error("Cannot load key and cert - ", err)
		return
	}
	config := &tls.Config{Certificates: []tls.Certificate{cer}}
	https_server := &http.Server{Addr: ":" + strconv.Itoa(https_port), TLSConfig: config, Handler: nil}

	// Run http
	go func() {
		log.Info("Starting http service...")
		err := http_server.ListenAndServe()
		if err == http.ErrServerClosed { // graceful shutdown
			log.Info("http server shutdown...")
		} else if err != nil {
			log.Error("http server error: ", err)
		}
	}()

	//  Run https
	go func() {
		log.Info("Starting https service...")
		err := https_server.ListenAndServe()
		if err == http.ErrServerClosed { // graceful shutdown
			log.Info("https server shutdown...")
		} else if err != nil {
			log.Error("https server error: ", err)
		}
	}()
	check_tcp(strconv.Itoa(http_port))
	check_tcp(strconv.Itoa(https_port))

	go start_topic_writer(writer_control, data_out_channel)

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
		http_server.Shutdown(context.Background())
		https_server.Shutdown(context.Background())
		// Stopping jobs
		for key, _ := range TypeJobs {
			log.Info("Stopping type job:", key)
			for _, dp := range InfoTypes.ProdDataTypes {
				if key == dp.ID {
					remove_type_job(dp)
				}
			}
		}
	}()

	AppState = Running

	//Wait until all go routines has exited
	runtime.Goexit()

	fmt.Println("main routine exit")
	fmt.Println("server stopped")
}

func check_tcp(port string) {
	log.Info("Checking tcp port: ", port)
	for true {
		address := net.JoinHostPort("localhost", port)
		// 3 second timeout
		conn, err := net.DialTimeout("tcp", address, 3*time.Second)
		if err != nil {
			log.Info("Checking tcp port: ", port, " failed, retrying...")
		} else {
			if conn != nil {
				log.Info("Checking tcp port: ", port, " - OK")
				_ = conn.Close()
				return
			} else {
				log.Info("Checking tcp port: ", port, " failed, retrying...")
			}
		}
	}
}

//== Core functions ==//

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
	data := DataTypes{}
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

		json, err := json.Marshal(t1)
		if err != nil {
			log.Error("Cannot create json for type: ", data.ProdDataTypes[i].ID)
			log.Error("Registering producer: ", producer_instance_name, " - failed")
			return false
		} else {
			ok := send_http_request(json, http.MethodPut, "http://"+ics_server+"/data-producer/v1/info-types/"+data.ProdDataTypes[i].ID, true, creds_grant_type != "")
			if !ok {
				log.Error("Cannot register type: ", data.ProdDataTypes[i].ID)
				log.Error("Registering producer: ", producer_instance_name, " - failed")
				return false
			}
			new_type_names = append(new_type_names, data.ProdDataTypes[i].ID)
		}

	}

	log.Debug("Registering types: ", new_type_names)
	m := make(map[string]interface{})
	m["supported_info_types"] = new_type_names
	m["info_job_callback_url"] = "http://" + self + "/callbacks/job/" + producer_instance_name
	m["info_producer_supervision_callback_url"] = "http://" + self + "/callbacks/supervision/" + producer_instance_name

	json, err := json.Marshal(m)
	if err != nil {
		log.Error("Cannot create json for producer: ", producer_instance_name)
		log.Error("Registering producer: ", producer_instance_name, " - failed")
		return false
	}
	ok := send_http_request(json, http.MethodPut, "http://"+ics_server+"/data-producer/v1/info-producers/"+producer_instance_name, true, creds_grant_type != "")
	if !ok {
		log.Error("Cannot register producer: ", producer_instance_name)
		log.Error("Registering producer: ", producer_instance_name, " - failed")
		return false
	}
	datalock.Lock()
	defer datalock.Unlock()

	var current_type_names []string
	for _, v := range InfoTypes.ProdDataTypes {
		current_type_names = append(current_type_names, v.ID)
		if contains_str(new_type_names, v.ID) {
			//Type exist
			log.Debug("Type ", v.ID, " exists")
			create_ext_job(v)
		} else {
			//Type is removed
			log.Info("Removing type job for type: ", v.ID, " Type not in configuration")
			remove_type_job(v)
		}
	}

	for _, v := range data.ProdDataTypes {
		if contains_str(current_type_names, v.ID) {
			//Type exist
			log.Debug("Type ", v.ID, " exists")
			create_ext_job(v)
		} else {
			//Type is new
			log.Info("Adding type job for type: ", v.ID, " Type added to configuration")
			start_type_job(v)
		}
	}

	InfoTypes = data
	log.Debug("Datatypes: ", InfoTypes)

	log.Info("Registering producer: ", producer_instance_name, " - OK")
	return true
}

func remove_type_job(dp DataType) {
	log.Info("Removing type job: ", dp.ID)
	j, ok := TypeJobs[dp.ID]
	if ok {
		j.reader_control <- ReaderControl{"EXIT"}
	}

	if dp.ext_job_created == true {
		dp.ext_job_id = dp.InputJobType + "_" + generate_uuid_from_type(dp.InputJobType)
		ok := send_http_request(*dp.ext_job, http.MethodDelete, "http://"+ics_server+"/data-consumer/v1/info-jobs/"+dp.ext_job_id, true, creds_grant_type != "")
		if !ok {
			log.Error("Cannot delete job: ", dp.ext_job_id)
		}
		dp.ext_job_created = false
		dp.ext_job = nil
	}

}

func start_type_job(dp DataType) {
	log.Info("Starting type job: ", dp.ID)
	job_record := TypeJobRecord{}

	job_record.job_control = make(chan JobControl, 1)
	job_record.reader_control = make(chan ReaderControl, 1)
	job_record.data_in_channel = make(chan *KafkaPayload, reader_queue_length)
	job_record.InfoType = dp.ID
	job_record.InputTopic = dp.KafkaInputTopic
	job_record.groupId = "kafka-procon-" + dp.ID
	job_record.clientId = dp.ID + "-" + os.Getenv("KP")
	var stats TypeJobStats
	job_record.statistics = &stats

	switch dp.ID {
	case "xml-file-data-to-filestore":
		go start_job_xml_file_data(dp.ID, job_record.job_control, job_record.data_in_channel, data_out_channel, "", "pm-files-json")
	case "xml-file-data":
		go start_job_xml_file_data(dp.ID, job_record.job_control, job_record.data_in_channel, data_out_channel, files_volume, "")
	case "json-file-data-from-filestore":
		go start_job_json_file_data(dp.ID, job_record.job_control, job_record.data_in_channel, data_out_channel, true)
	case "json-file-data":
		go start_job_json_file_data(dp.ID, job_record.job_control, job_record.data_in_channel, data_out_channel, false)
	case "json-file-data-from-filestore-to-influx":
		go start_job_json_file_data_influx(dp.ID, job_record.job_control, job_record.data_in_channel, true)

	default:
	}

	go start_topic_reader(dp.KafkaInputTopic, dp.ID, job_record.reader_control, job_record.data_in_channel, job_record.groupId, job_record.clientId, &stats)

	TypeJobs[dp.ID] = job_record
	log.Debug("Type job input type: ", dp.InputJobType)
	create_ext_job(dp)
}

func create_ext_job(dp DataType) {
	if dp.InputJobType != "" {
		jb := make(map[string]interface{})
		jb["info_type_id"] = dp.InputJobType
		jb["job_owner"] = "console" //TODO:
		jb["status_notification_uri"] = "http://callback:80/post"
		jb1 := make(map[string]interface{})
		jb["job_definition"] = jb1
		jb1["kafkaOutputTopic"] = dp.InputJobDefinition.KafkaOutputTopic

		json, err := json.Marshal(jb)
		dp.ext_job_created = false
		dp.ext_job = nil
		if err != nil {
			log.Error("Cannot create json for type: ", dp.InputJobType)
			return
		}

		dp.ext_job_id = dp.InputJobType + "_" + generate_uuid_from_type(dp.InputJobType)
		ok := false
		for !ok {
			ok = send_http_request(json, http.MethodPut, "http://"+ics_server+"/data-consumer/v1/info-jobs/"+dp.ext_job_id, true, creds_grant_type != "")
			if !ok {
				log.Error("Cannot register job: ", dp.InputJobType)
			}
		}
		log.Debug("Registered job ok: ", dp.InputJobType)
		dp.ext_job_created = true
		dp.ext_job = &json
	}
}

func remove_info_job(jobid string) {
	log.Info("Removing info job: ", jobid)
	filter := Filter{}
	filter.JobId = jobid
	jc := JobControl{}
	jc.command = "REMOVE-FILTER"
	jc.filter = filter
	infoJob := InfoJobs[jobid]
	typeJob := TypeJobs[infoJob.job_info.InfoTypeIdentity]
	typeJob.job_control <- jc
	delete(InfoJobs, jobid)

}

// == Helper functions ==//

// Function to check the status of a mutex lock
func MutexLocked(m *sync.Mutex) bool {
	state := reflect.ValueOf(m).Elem().FieldByName("state")
	return state.Int()&mutexLocked == mutexLocked
}

// Test if slice contains a string
func contains_str(s []string, e string) bool {
	for _, a := range s {
		if a == e {
			return true
		}
	}
	return false
}

// Send a http request with json (json may be nil)
func send_http_request(json []byte, method string, url string, retry bool, useAuth bool) bool {

	// set the HTTP method, url, and request body
	var req *http.Request
	var err error
	if json == nil {
		req, err = http.NewRequest(method, url, http.NoBody)
	} else {
		req, err = http.NewRequest(method, url, bytes.NewBuffer(json))
		req.Header.Set("Content-Type", "application/json; charset=utf-8")
	}
	if err != nil {
		log.Error("Cannot create http request, method: ", method, " url: ", url)
		return false
	}

	if useAuth {
		token, err := fetch_token()
		if err != nil {
			log.Error("Cannot fetch token for http request: ", err)
			return false
		}
		req.Header.Set("Authorization", "Bearer "+token.TokenValue)
	}

	log.Debug("HTTP request: ", req)

	log.Debug("Sending http request")
	resp, err2 := httpclient.Do(req)
	if err2 != nil {
		log.Error("Http request error: ", err2)
		log.Error("Cannot send http request method: ", method, " url: ", url)
	} else {
		if resp.StatusCode == 200 || resp.StatusCode == 201 || resp.StatusCode == 204 {
			log.Debug("Accepted http status: ", resp.StatusCode)
			resp.Body.Close()
			return true
		}
		log.Debug("HTTP resp: ", resp)
		resp.Body.Close()
	}
	return false
}

func fetch_token() (*kafka.OAuthBearerToken, error) {
	log.Debug("Get token inline")
	conf := &clientcredentials.Config{
		ClientID:     creds_client_id,
		ClientSecret: creds_client_secret,
		TokenURL:     creds_service_url,
	}
	token, err := conf.Token(context.Background())
	if err != nil {
		log.Warning("Cannot fetch access token: ", err)
		return nil, err
	}
	extensions := map[string]string{}
	log.Debug("=====================================================")
	log.Debug("token: ", token)
	log.Debug("=====================================================")
	log.Debug("TokenValue: ", token.AccessToken)
	log.Debug("=====================================================")
	log.Debug("Expiration: ", token.Expiry)
	t := token.Expiry
	oauthBearerToken := kafka.OAuthBearerToken{
		TokenValue: token.AccessToken,
		Expiration: t,
		Extensions: extensions,
	}

	return &oauthBearerToken, nil
}

// Function to print memory details
// https://pkg.go.dev/runtime#MemStats
func PrintMemUsage() {
	if log.IsLevelEnabled(log.DebugLevel) || log.IsLevelEnabled(log.TraceLevel) {
		var m runtime.MemStats
		runtime.ReadMemStats(&m)
		fmt.Printf("Alloc = %v MiB", bToMb(m.Alloc))
		fmt.Printf("\tTotalAlloc = %v MiB", bToMb(m.TotalAlloc))
		fmt.Printf("\tSys = %v MiB", bToMb(m.Sys))
		fmt.Printf("\tNumGC = %v\n", m.NumGC)
		fmt.Printf("HeapSys = %v MiB", bToMb(m.HeapSys))
		fmt.Printf("\tStackSys = %v MiB", bToMb(m.StackSys))
		fmt.Printf("\tMSpanSys = %v MiB", bToMb(m.MSpanSys))
		fmt.Printf("\tMCacheSys = %v MiB", bToMb(m.MCacheSys))
		fmt.Printf("\tBuckHashSys = %v MiB", bToMb(m.BuckHashSys))
		fmt.Printf("\tGCSys = %v MiB", bToMb(m.GCSys))
		fmt.Printf("\tOtherSys = %v MiB\n", bToMb(m.OtherSys))
	}
}

func bToMb(b uint64) uint64 {
	return b / 1024 / 1024
}

func generate_uuid_from_type(s string) string {
	if len(s) > 16 {
		s = s[:16]
	}
	for len(s) < 16 {
		s = s + "0"
	}
	b := []byte(s)
	b = b[:16]
	uuid, _ := uuid.FromBytes(b)
	return uuid.String()
}

// Write gzipped data to a Writer
func gzipWrite(w io.Writer, data *[]byte) error {
	gw, err1 := gzip.NewWriterLevel(w, gzip.BestSpeed)

	if err1 != nil {
		return err1
	}
	defer gw.Close()
	_, err2 := gw.Write(*data)
	return err2
}

// Write gunzipped data from Reader to a Writer
func gunzipReaderToWriter(w io.Writer, data io.Reader) error {
	gr, err1 := gzip.NewReader(data)

	if err1 != nil {
		return err1
	}
	defer gr.Close()
	data2, err2 := io.ReadAll(gr)
	if err2 != nil {
		return err2
	}
	_, err3 := w.Write(data2)
	if err3 != nil {
		return err3
	}
	return nil
}

func create_minio_bucket(mc *minio.Client, client_id string, bucket string) error {
	tctx := context.Background()
	err := mc.MakeBucket(tctx, bucket, minio.MakeBucketOptions{Region: bucket_location})
	if err != nil {
		// Check to see if we already own this bucket (which happens if you run this twice)
		exists, errBucketExists := mc.BucketExists(tctx, bucket)
		if errBucketExists == nil && exists {
			log.Debug("Already own bucket:", bucket)
			add_bucket(client_id, bucket)
			return nil
		} else {
			log.Error("Cannot create or check bucket ", bucket, " in minio client", err)
			return err
		}
	}
	log.Debug("Successfully created bucket: ", bucket)
	add_bucket(client_id, bucket)
	return nil
}

func check_minio_bucket(mc *minio.Client, client_id string, bucket string) bool {
	ok := bucket_exist(client_id, bucket)
	if ok {
		return true
	}
	tctx := context.Background()
	exists, err := mc.BucketExists(tctx, bucket)
	if err == nil && exists {
		log.Debug("Already own bucket:", bucket)
		return true
	}
	log.Error("Bucket does not exist, bucket ", bucket, " in minio client", err)
	return false
}

func add_bucket(minio_id string, bucket string) {
	datalock.Lock()
	defer datalock.Unlock()

	b, ok := minio_bucketlist[minio_id]
	if !ok {
		b = Minio_buckets{}
		b.Buckets = make(map[string]bool)
	}
	b.Buckets[bucket] = true
	minio_bucketlist[minio_id] = b
}

func bucket_exist(minio_id string, bucket string) bool {
	datalock.Lock()
	defer datalock.Unlock()

	b, ok := minio_bucketlist[minio_id]
	if !ok {
		return false
	}
	_, ok = b.Buckets[bucket]
	return ok
}

//== http api functions ==//

// create/update job
func create_job(w http.ResponseWriter, req *http.Request) {
	log.Debug("Create job, http method: ", req.Method)
	if req.Method != http.MethodPost {
		log.Error("Create job, http method not allowed")
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	ct := req.Header.Get("Content-Type")
	if ct != "application/json" {
		log.Error("Create job, bad content type")
		http.Error(w, "Bad content type", http.StatusBadRequest)
		return
	}

	var t InfoJobDataType
	err := json.NewDecoder(req.Body).Decode(&t)
	if err != nil {
		log.Error("Create job, cannot parse json,", err)
		http.Error(w, "Cannot parse json", http.StatusBadRequest)
		return
	}
	log.Debug("Creating job, id: ", t.InfoJobIdentity)
	datalock.Lock()
	defer datalock.Unlock()

	job_id := t.InfoJobIdentity
	job_record, job_found := InfoJobs[job_id]
	type_job_record, found_type := TypeJobs[t.InfoTypeIdentity]
	if !job_found {
		if !found_type {
			log.Error("Type ", t.InfoTypeIdentity, " does not exist")
			http.Error(w, "Type "+t.InfoTypeIdentity+" does not exist", http.StatusBadRequest)
			return
		}
	} else if t.InfoTypeIdentity != job_record.job_info.InfoTypeIdentity {
		log.Error("Job cannot change type")
		http.Error(w, "Job cannot change type", http.StatusBadRequest)
		return
	} else if t.InfoJobData.KafkaOutputTopic != job_record.job_info.InfoJobData.KafkaOutputTopic {
		log.Error("Job cannot change topic")
		http.Error(w, "Job cannot change topic", http.StatusBadRequest)
		return
	} else if !found_type {
		//Should never happen, if the type is removed then job is stopped
		log.Error("Type ", t.InfoTypeIdentity, " does not exist")
		http.Error(w, "Type "+t.InfoTypeIdentity+" does not exist", http.StatusBadRequest)
		return
	}

	if !job_found {
		job_record = InfoJobRecord{}
		job_record.job_info = t
		output_topic := t.InfoJobData.KafkaOutputTopic
		job_record.output_topic = t.InfoJobData.KafkaOutputTopic
		log.Debug("Starting infojob ", job_id, ", type ", t.InfoTypeIdentity, ", output topic", output_topic)

		var stats InfoJobStats
		job_record.statistics = &stats

		filter := Filter{}
		filter.JobId = job_id
		filter.OutputTopic = job_record.output_topic

		jc := JobControl{}

		jc.command = "ADD-FILTER"

		if t.InfoTypeIdentity == "json-file-data-from-filestore" || t.InfoTypeIdentity == "json-file-data" || t.InfoTypeIdentity == "json-file-data-from-filestore-to-influx" {
			fm := FilterMaps{}
			fm.sourceNameMap = make(map[string]bool)
			fm.measObjClassMap = make(map[string]bool)
			fm.measObjInstIdsMap = make(map[string]bool)
			fm.measTypesMap = make(map[string]bool)
			if t.InfoJobData.FilterParams.MeasuredEntityDns != nil {
				for _, v := range t.InfoJobData.FilterParams.MeasuredEntityDns {
					fm.sourceNameMap[v] = true
				}
			}
			if t.InfoJobData.FilterParams.MeasObjClass != nil {
				for _, v := range t.InfoJobData.FilterParams.MeasObjClass {
					fm.measObjClassMap[v] = true
				}
			}
			if t.InfoJobData.FilterParams.MeasObjInstIds != nil {
				for _, v := range t.InfoJobData.FilterParams.MeasObjInstIds {
					fm.measObjInstIdsMap[v] = true
				}
			}
			if t.InfoJobData.FilterParams.MeasTypes != nil {
				for _, v := range t.InfoJobData.FilterParams.MeasTypes {
					fm.measTypesMap[v] = true
				}
			}
			filter.filter = fm
		}
		if t.InfoTypeIdentity == "json-file-data-from-filestore-to-influx" {
			influxparam := InfluxJobParameters{}
			influxparam.DbUrl = t.InfoJobData.DbUrl
			influxparam.DbOrg = t.InfoJobData.DbOrg
			influxparam.DbBucket = t.InfoJobData.DbBucket
			influxparam.DbToken = t.InfoJobData.DbToken
			filter.influxParameters = influxparam
		}

		jc.filter = filter
		InfoJobs[job_id] = job_record

		type_job_record.job_control <- jc

	} else {
		//TODO
		//Update job
	}
}

// delete job
func delete_job(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodDelete {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	datalock.Lock()
	defer datalock.Unlock()

	vars := mux.Vars(req)

	if id, ok := vars["job_id"]; ok {
		if _, ok := InfoJobs[id]; ok {
			remove_info_job(id)
			w.WriteHeader(http.StatusNoContent)
			log.Info("Job ", id, " deleted")
			return
		}
	}
	w.WriteHeader(http.StatusNotFound)
}

// job supervision
func supervise_job(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	datalock.Lock()
	defer datalock.Unlock()

	vars := mux.Vars(req)

	log.Debug("Supervising, job: ", vars["job_id"])
	if id, ok := vars["job_id"]; ok {
		if _, ok := InfoJobs[id]; ok {
			log.Debug("Supervision ok, job", id)
			return
		}
	}
	w.WriteHeader(http.StatusNotFound)
}

// producer supervision
func supervise_producer(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}

	w.WriteHeader(http.StatusOK)
}

// producer statistics, all jobs
func statistics(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	m := make(map[string]interface{})
	log.Debug("producer statictics, locked? ", MutexLocked(&datalock))
	datalock.Lock()
	defer datalock.Unlock()
	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	m["number-of-jobs"] = len(InfoJobs)
	m["number-of-types"] = len(InfoTypes.ProdDataTypes)
	qm := make(map[string]interface{})
	m["jobs"] = qm
	for key, elem := range InfoJobs {
		jm := make(map[string]interface{})
		qm[key] = jm
		jm["type"] = elem.job_info.InfoTypeIdentity
		typeJob := TypeJobs[elem.job_info.InfoTypeIdentity]
		jm["groupId"] = typeJob.groupId
		jm["clientID"] = typeJob.clientId
		jm["input topic"] = typeJob.InputTopic
		jm["input queue length - job ("+fmt.Sprintf("%v", cap(typeJob.data_in_channel))+")"] = len(typeJob.data_in_channel)
		jm["output topic"] = elem.output_topic
		jm["output queue length - producer ("+fmt.Sprintf("%v", cap(data_out_channel))+")"] = len(data_out_channel)
		jm["msg_in (type)"] = typeJob.statistics.in_msg_cnt
		jm["msg_out (job)"] = elem.statistics.out_msg_cnt

	}
	json, err := json.Marshal(m)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		log.Error("Cannot marshal statistics json")
		return
	}
	_, err = w.Write(json)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		log.Error("Cannot send statistics json")
		return
	}
}

// Simple alive check
func alive(w http.ResponseWriter, req *http.Request) {
	//Alive check
}

// Get/Set logging level
func logging_level(w http.ResponseWriter, req *http.Request) {
	vars := mux.Vars(req)
	if level, ok := vars["level"]; ok {
		if req.Method == http.MethodPut {
			switch level {
			case "trace":
				log.SetLevel(log.TraceLevel)
			case "debug":
				log.SetLevel(log.DebugLevel)
			case "info":
				log.SetLevel(log.InfoLevel)
			case "warn":
				log.SetLevel(log.WarnLevel)
			case "error":
				log.SetLevel(log.ErrorLevel)
			case "fatal":
				log.SetLevel(log.FatalLevel)
			case "panic":
				log.SetLevel(log.PanicLevel)
			default:
				w.WriteHeader(http.StatusNotFound)
			}
		} else {
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	} else {
		if req.Method == http.MethodGet {
			msg := "none"
			if log.IsLevelEnabled(log.PanicLevel) {
				msg = "panic"
			} else if log.IsLevelEnabled(log.FatalLevel) {
				msg = "fatal"
			} else if log.IsLevelEnabled(log.ErrorLevel) {
				msg = "error"
			} else if log.IsLevelEnabled(log.WarnLevel) {
				msg = "warn"
			} else if log.IsLevelEnabled(log.InfoLevel) {
				msg = "info"
			} else if log.IsLevelEnabled(log.DebugLevel) {
				msg = "debug"
			} else if log.IsLevelEnabled(log.TraceLevel) {
				msg = "trace"
			}
			w.Header().Set("Content-Type", "application/text")
			w.Write([]byte(msg))
		} else {
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	}
}

func start_topic_reader(topic string, type_id string, control_ch chan ReaderControl, data_ch chan *KafkaPayload, gid string, cid string, stats *TypeJobStats) {

	log.Info("Topic reader starting, topic: ", topic, " for type: ", type_id)

	topic_ok := false
	var c *kafka.Consumer = nil
	running := true

	for topic_ok == false {

		select {
		case reader_ctrl := <-control_ch:
			if reader_ctrl.command == "EXIT" {
				log.Info("Topic reader on topic: ", topic, " for type: ", type_id, " - stopped")
				data_ch <- nil //Signal to job handler
				running = false
				return
			}
		case <-time.After(1 * time.Second):
			if !running {
				return
			}
			if c == nil {
				c = create_kafka_consumer(type_id, gid, cid)
				if c == nil {
					log.Info("Cannot start consumer on topic: ", topic, " for type: ", type_id, " - retrying")
				} else {
					log.Info("Consumer started on topic: ", topic, " for type: ", type_id)
				}
			}
			if c != nil && topic_ok == false {
				err := c.SubscribeTopics([]string{topic}, nil)
				if err != nil {
					log.Info("Topic reader cannot start subscribing on topic: ", topic, " for type: ", type_id, " - retrying --  error details: ", err)
				} else {
					log.Info("Topic reader subscribing on topic: ", topic, " for type: ", type_id)
					topic_ok = true
				}
			}
		}
	}
	log.Info("Topic reader ready on topic: ", topic, " for type: ", type_id)

	var event_chan = make(chan int)
	go func() {
		for {
			select {
			case evt := <-c.Events():
				switch evt.(type) {
				case kafka.OAuthBearerTokenRefresh:
					log.Debug("New consumer token needed: ", evt)
					token, err := fetch_token()
					if err != nil {
						log.Warning("Cannot cannot fetch token: ", err)
						c.SetOAuthBearerTokenFailure(err.Error())
					} else {
						setTokenError := c.SetOAuthBearerToken(*token)
						if setTokenError != nil {
							log.Warning("Cannot cannot set token: ", setTokenError)
							c.SetOAuthBearerTokenFailure(setTokenError.Error())
						}
					}
				default:
					log.Debug("Dumping topic reader event on topic: ", topic, " for type: ", type_id, " evt: ", evt.String())
				}

			case msg := <-event_chan:
				if msg == 0 {
					return
				}
			case <-time.After(1 * time.Second):
				if !running {
					return
				}
			}
		}
	}()

	go func() {
		for {
			for {
				select {
				case reader_ctrl := <-control_ch:
					if reader_ctrl.command == "EXIT" {
						event_chan <- 0
						log.Debug("Topic reader on topic: ", topic, " for type: ", type_id, " - stopped")
						data_ch <- nil //Signal to job handler
						defer c.Close()
						return
					}
				default:

					ev := c.Poll(1000)
					if ev == nil {
						log.Debug("Topic Reader for type: ", type_id, "  Nothing to consume on topic: ", topic)
						continue
					}
					switch e := ev.(type) {
					case *kafka.Message:
						var kmsg KafkaPayload
						kmsg.msg = e

						c.Commit()

						data_ch <- &kmsg
						stats.in_msg_cnt++
						log.Debug("Reader msg: ", &kmsg)
						log.Debug("Reader - data_ch ", data_ch)
					case kafka.Error:
						fmt.Fprintf(os.Stderr, "%% Error: %v: %v\n", e.Code(), e)

					case kafka.OAuthBearerTokenRefresh:
						log.Debug("New consumer token needed: ", ev)
						token, err := fetch_token()
						if err != nil {
							log.Warning("Cannot cannot fetch token: ", err)
							c.SetOAuthBearerTokenFailure(err.Error())
						} else {
							setTokenError := c.SetOAuthBearerToken(*token)
							if setTokenError != nil {
								log.Warning("Cannot cannot set token: ", setTokenError)
								c.SetOAuthBearerTokenFailure(setTokenError.Error())
							}
						}
					default:
						fmt.Printf("Ignored %v\n", e)
					}
				}
			}
		}
	}()
}

func start_topic_writer(control_ch chan WriterControl, data_ch chan *KafkaPayload) {

	var kafka_producer *kafka.Producer

	running := true
	log.Info("Topic writer starting")

	// Wait for kafka producer to become available - and be prepared to exit the writer
	for kafka_producer == nil {
		select {
		case writer_ctl := <-control_ch:
			if writer_ctl.command == "EXIT" {
				//ignore cmd
			}
		default:
			kafka_producer = start_producer()
			if kafka_producer == nil {
				log.Debug("Could not start kafka producer - retrying")
				time.Sleep(1 * time.Second)
			} else {
				log.Debug("Kafka producer started")
			}
		}
	}

	var event_chan = make(chan int)
	go func() {
		for {
			select {
			case evt := <-kafka_producer.Events():
				switch evt.(type) {
				case *kafka.Message:
					m := evt.(*kafka.Message)

					if m.TopicPartition.Error != nil {
						log.Debug("Dumping topic writer event, failed: ", m.TopicPartition.Error)
					} else {
						log.Debug("Dumping topic writer event,  message to topic: ", *m.TopicPartition.Topic, " at offset: ", m.TopicPartition.Offset, " at partition: ", m.TopicPartition.Partition)
					}
				case kafka.Error:
					log.Debug("Dumping topic writer event, error: ", evt)
				case kafka.OAuthBearerTokenRefresh:
					log.Debug("New producer token needed: ", evt)
					token, err := fetch_token()
					if err != nil {
						log.Warning("Cannot cannot fetch token: ", err)
						kafka_producer.SetOAuthBearerTokenFailure(err.Error())
					} else {
						setTokenError := kafka_producer.SetOAuthBearerToken(*token)
						if setTokenError != nil {
							log.Warning("Cannot cannot set token: ", setTokenError)
							kafka_producer.SetOAuthBearerTokenFailure(setTokenError.Error())
						}
					}
				default:
					log.Debug("Dumping topic writer event, unknown: ", evt)
				}

			case msg := <-event_chan:
				if msg == 0 {
					return
				}
			case <-time.After(1 * time.Second):
				if !running {
					return
				}
			}
		}
	}()
	go func() {
		for {
			select {
			case writer_ctl := <-control_ch:
				if writer_ctl.command == "EXIT" {
					// ignore - wait for channel signal
				}

			case kmsg := <-data_ch:
				if kmsg == nil {
					event_chan <- 0
					log.Info("Topic writer stopped by channel signal - start_topic_writer")
					defer kafka_producer.Close()
					return
				}

				retries := 10
				msg_ok := false
				var err error
				for retry := 1; retry <= retries && msg_ok == false; retry++ {
					err = kafka_producer.Produce(&kafka.Message{
						TopicPartition: kafka.TopicPartition{Topic: &kmsg.topic, Partition: kafka.PartitionAny},
						Value:          kmsg.msg.Value, Key: kmsg.msg.Key}, nil)

					if err == nil {
						incr_out_msg_cnt(kmsg.jobid)
						msg_ok = true
						log.Debug("Topic writer, msg sent ok on topic: ", kmsg.topic)
					} else {
						log.Info("Topic writer failed to send message on topic: ", kmsg.topic, " - Retrying. Error details: ", err)
						time.Sleep(time.Duration(retry) * time.Second)
					}
				}
				if !msg_ok {
					log.Error("Topic writer failed to send message on topic: ", kmsg.topic, " - Msg discarded. Error details: ", err)
				}
			case <-time.After(1000 * time.Millisecond):
				if !running {
					return
				}
			}
		}
	}()
}

func create_kafka_consumer(type_id string, gid string, cid string) *kafka.Consumer {
	var cm kafka.ConfigMap
	if creds_grant_type == "" {
		log.Info("Creating kafka SASL plain text consumer for type: ", type_id)
		cm = kafka.ConfigMap{
			"bootstrap.servers":  bootstrapserver,
			"group.id":           gid,
			"client.id":          cid,
			"auto.offset.reset":  "latest",
			"enable.auto.commit": false,
		}
	} else {
		log.Info("Creating kafka SASL plain text consumer for type: ", type_id)
		cm = kafka.ConfigMap{
			"bootstrap.servers":  bootstrapserver,
			"group.id":           gid,
			"client.id":          cid,
			"auto.offset.reset":  "latest",
			"enable.auto.commit": false,
			"sasl.mechanism":     "OAUTHBEARER",
			"security.protocol":  "SASL_PLAINTEXT",
		}
	}
	c, err := kafka.NewConsumer(&cm)

	if err != nil {
		log.Error("Cannot create kafka consumer for type: ", type_id, ", error details: ", err)
		return nil
	}

	log.Info("Created kafka consumer for type: ", type_id, " OK")
	return c
}

// Start kafka producer
func start_producer() *kafka.Producer {
	log.Info("Creating kafka producer")

	var cm kafka.ConfigMap
	if creds_grant_type == "" {
		log.Info("Creating kafka SASL plain text producer")
		cm = kafka.ConfigMap{
			"bootstrap.servers": bootstrapserver,
		}
	} else {
		log.Info("Creating kafka SASL plain text producer")
		cm = kafka.ConfigMap{
			"bootstrap.servers": bootstrapserver,
			"sasl.mechanism":    "OAUTHBEARER",
			"security.protocol": "SASL_PLAINTEXT",
		}
	}

	p, err := kafka.NewProducer(&cm)
	if err != nil {
		log.Error("Cannot create kafka producer,", err)
		return nil
	}
	return p
}

func start_adminclient() *kafka.AdminClient {
	log.Info("Creating kafka admin client")
	a, err := kafka.NewAdminClient(&kafka.ConfigMap{"bootstrap.servers": bootstrapserver})
	if err != nil {
		log.Error("Cannot create kafka admin client,", err)
		return nil
	}
	return a
}

func create_minio_client(id string) (*minio.Client, *error) {
	log.Debug("Get minio client")
	minio_client, err := minio.New(filestore_server, &minio.Options{
		Secure: false,
		Creds:  credentials.NewStaticV4(filestore_user, filestore_pwd, ""),
	})
	if err != nil {
		log.Error("Cannot create minio client, ", err)
		return nil, &err
	}
	return minio_client, nil
}

func incr_out_msg_cnt(jobid string) {
	j, ok := InfoJobs[jobid]
	if ok {
		j.statistics.out_msg_cnt++
	}
}

func start_job_xml_file_data(type_id string, control_ch chan JobControl, data_in_ch chan *KafkaPayload, data_out_channel chan *KafkaPayload, fvolume string, fsbucket string) {

	log.Info("Type job", type_id, " started")

	filters := make(map[string]Filter)
	topic_list := make(map[string]string)
	var mc *minio.Client
	const mc_id = "mc_" + "start_job_xml_file_data"
	running := true
	for {
		select {
		case job_ctl := <-control_ch:
			log.Debug("Type job ", type_id, " new cmd received ", job_ctl.command)
			switch job_ctl.command {
			case "EXIT":
				//ignore cmd - handled by channel signal
			case "ADD-FILTER":
				filters[job_ctl.filter.JobId] = job_ctl.filter
				log.Debug("Type job ", type_id, " updated, topic: ", job_ctl.filter.OutputTopic, " jobid: ", job_ctl.filter.JobId)

				tmp_topic_list := make(map[string]string)
				for k, v := range topic_list {
					tmp_topic_list[k] = v
				}
				tmp_topic_list[job_ctl.filter.JobId] = job_ctl.filter.OutputTopic
				topic_list = tmp_topic_list
			case "REMOVE-FILTER":
				log.Debug("Type job ", type_id, " removing job: ", job_ctl.filter.JobId)

				tmp_topic_list := make(map[string]string)
				for k, v := range topic_list {
					tmp_topic_list[k] = v
				}
				delete(tmp_topic_list, job_ctl.filter.JobId)
				topic_list = tmp_topic_list
			}

		case msg := <-data_in_ch:
			if msg == nil {
				log.Info("Type job ", type_id, " stopped by channel signal -  start_job_xml_file_data")

				running = false
				return
			}
			if fsbucket != "" && fvolume == "" {
				if mc == nil {
					var err *error
					mc, err = create_minio_client(mc_id)
					if err != nil {
						log.Debug("Cannot create minio client for type job: ", type_id)
					}
				}
			}
			jobLimiterChan <- struct{}{}
			go run_xml_job(type_id, msg, "gz", data_out_channel, topic_list, jobLimiterChan, fvolume, fsbucket, mc, mc_id)

		case <-time.After(1 * time.Second):
			if !running {
				return
			}
		}
	}
}

func run_xml_job(type_id string, msg *KafkaPayload, outputCompression string, data_out_channel chan *KafkaPayload, topic_list map[string]string, jobLimiterChan chan struct{}, fvolume string, fsbucket string, mc *minio.Client, mc_id string) {
	defer func() {
		<-jobLimiterChan
	}()
	PrintMemUsage()

	if fvolume == "" && fsbucket == "" {
		log.Error("Type job: ", type_id, " cannot run, neither file volume nor filestore is set - discarding message")
		return
	} else if (fvolume != "") && (fsbucket != "") {
		log.Error("Type job: ", type_id, " cannot run with output to both file volume and filestore bucket - discarding message")
		return
	}

	start := time.Now()
	var evt_data XmlFileEventHeader

	err := jsoniter.Unmarshal(msg.msg.Value, &evt_data)
	if err != nil {
		log.Error("Cannot parse XmlFileEventHeader for type job: ", type_id, " - discarding message, error details", err)
		return
	}
	log.Debug("Unmarshal file-collect event for type job: ", type_id, " time: ", time.Since(start).String())

	var reader io.Reader

	INPUTBUCKET := "ropfiles"

	filename := ""
	if fvolume != "" {
		filename = fvolume + "/" + evt_data.Name
		fi, err := os.Open(filename)

		if err != nil {
			log.Error("File ", filename, " - cannot be opened for type job: ", type_id, " - discarding message, error details: ", err)
			return
		}
		defer fi.Close()
		reader = fi
	} else {
		filename = evt_data.Name
		if mc != nil {
			tctx := context.Background()
			mr, err := mc.GetObject(tctx, INPUTBUCKET, filename, minio.GetObjectOptions{})
			if err != nil {
				log.Error("Cannot get: ", filename, " for type job: ", type_id, " - discarding message, error details: ", err)
				return
			}
			if mr == nil {
				log.Error("Cannot get: ", filename, " for type job: ", type_id, " - minio get object returned null reader -  discarding message, error details: ", err)
				return
			}
			reader = mr
			defer mr.Close()
		} else {
			log.Error("Cannot get: ", filename, " for type job: ", type_id, " - no minio client -  discarding message")
			return
		}
	}

	if reader == nil {
		log.Error("Cannot get: ", filename, " - null reader")
		return
	}
	var file_bytes []byte
	if strings.HasSuffix(filename, "gz") {
		start := time.Now()
		var buf3 bytes.Buffer
		errb := gunzipReaderToWriter(&buf3, reader)
		if errb != nil {
			log.Error("Cannot gunzip file ", filename, " - discarding message, ", errb)
			return
		}
		file_bytes = buf3.Bytes()
		log.Debug("Gunzip file: ", filename, "time:", time.Since(start).String(), "len: ", len(file_bytes))

	} else {
		var buf3 bytes.Buffer
		_, err2 := io.Copy(&buf3, reader)
		if err2 != nil {
			log.Error("File ", filename, " - cannot be read, discarding message, ", err)
			return
		}
		file_bytes = buf3.Bytes()
	}
	start = time.Now()
	b, err := xml_to_json_conv(&file_bytes, &evt_data)
	if err != nil {
		log.Error("Cannot convert file ", evt_data.Name, " - discarding message, ", err)
		return
	}
	log.Debug("Converted file to json: ", filename, " time", time.Since(start).String(), "len;", len(b))

	new_fn := evt_data.Name + os.Getenv("KP") + ".json"
	if outputCompression == "gz" {
		new_fn = new_fn + ".gz"
		start = time.Now()
		var buf bytes.Buffer
		err = gzipWrite(&buf, &b)
		if err != nil {
			log.Error("Cannot gzip file ", new_fn, " - discarding message, ", err)
			return
		}
		b = buf.Bytes()
		log.Debug("Gzip file:  ", new_fn, " time: ", time.Since(start).String(), "len:", len(file_bytes))

	}
	start = time.Now()

	if fvolume != "" {
		//Store on disk
		err = os.WriteFile(fvolume+"/"+new_fn, b, 0644)
		if err != nil {
			log.Error("Cannot write file ", new_fn, " - discarding message,", err)
			return
		}
		log.Debug("Write file to disk: "+new_fn, "time: ", time.Since(start).String(), " len: ", len(file_bytes))
	} else if fsbucket != "" {
		// Store in minio
		objectName := new_fn
		if mc != nil {

			contentType := "application/json"
			if strings.HasSuffix(objectName, ".gz") {
				contentType = "application/gzip"
			}

			// Upload the xml file with PutObject
			r := bytes.NewReader(b)
			tctx := context.Background()
			if check_minio_bucket(mc, mc_id, fsbucket) == false {
				err := create_minio_bucket(mc, mc_id, fsbucket)
				if err != nil {
					log.Error("Cannot create bucket: ", fsbucket, ", ", err)
					return
				}
			}
			ok := false
			for i := 1; i < 64 && ok == false; i = i * 2 {
				info, err := mc.PutObject(tctx, fsbucket, objectName, r, int64(len(b)), minio.PutObjectOptions{ContentType: contentType})
				if err != nil {

					if i == 1 {
						log.Warn("Cannot upload (first attempt): ", objectName, ", ", err)
					} else {
						log.Warn("Cannot upload (retry): ", objectName, ", ", err)
					}
					time.Sleep(time.Duration(i) * time.Second)
				} else {
					log.Debug("Store ", objectName, " in filestore, time: ", time.Since(start).String())
					log.Debug("Successfully uploaded: ", objectName, " of size:", info.Size)
					ok = true
				}
			}
			if !ok {
				log.Error("Cannot upload : ", objectName, ", ", err)
			}
		} else {
			log.Error("Cannot upload: ", objectName, ", no client")
		}
	}

	start = time.Now()
	if fvolume == "" {
		var fde FileDownloadedEvt
		fde.Filename = new_fn
		j, err := jsoniter.Marshal(fde)

		if err != nil {
			log.Error("Cannot marshal FileDownloadedEvt - discarding message, ", err)
			return
		}
		msg.msg.Value = j
	} else {
		var fde FileDownloadedEvt
		fde.Filename = new_fn
		j, err := jsoniter.Marshal(fde)

		if err != nil {
			log.Error("Cannot marshal FileDownloadedEvt - discarding message, ", err)
			return
		}
		msg.msg.Value = j
	}
	msg.msg.Key = []byte("\"" + evt_data.SourceName + "\"")
	log.Debug("Marshal file-collect event ", time.Since(start).String())

	for k, v := range topic_list {
		var kmsg *KafkaPayload = new(KafkaPayload)
		kmsg.msg = msg.msg
		kmsg.topic = v
		kmsg.jobid = k
		data_out_channel <- kmsg
	}
}

func xml_to_json_conv(f_byteValue *[]byte, xfeh *XmlFileEventHeader) ([]byte, error) {
	var f MeasCollecFile
	start := time.Now()
	err := xml.Unmarshal(*f_byteValue, &f)
	if err != nil {
		return nil, errors.New("Cannot unmarshal xml-file")
	}
	log.Debug("Unmarshal xml file XmlFileEvent: ", time.Since(start).String())

	start = time.Now()
	var pmfile PMJsonFile

	pmfile.Event.Perf3GppFields.Perf3GppFieldsVersion = "1.0"
	pmfile.Event.Perf3GppFields.MeasDataCollection.GranularityPeriod = 900
	pmfile.Event.Perf3GppFields.MeasDataCollection.MeasuredEntityUserName = ""
	pmfile.Event.Perf3GppFields.MeasDataCollection.MeasuredEntityDn = f.FileHeader.FileSender.LocalDn
	pmfile.Event.Perf3GppFields.MeasDataCollection.MeasuredEntitySoftwareVersion = f.MeasData.ManagedElement.SwVersion

	for _, it := range f.MeasData.MeasInfo {
		var mili MeasInfoList
		mili.MeasInfoID.SMeasInfoID = it.MeasInfoId
		for _, jt := range it.MeasType {
			mili.MeasTypes.SMeasTypesList = append(mili.MeasTypes.SMeasTypesList, jt.Text)
		}
		for _, jt := range it.MeasValue {
			var mv MeasValues
			mv.MeasObjInstID = jt.MeasObjLdn
			mv.SuspectFlag = jt.Suspect
			if jt.Suspect == "" {
				mv.SuspectFlag = "false"
			}
			for _, kt := range jt.R {
				ni, _ := strconv.Atoi(kt.P)
				nv := kt.Text
				mr := MeasResults{ni, nv}
				mv.MeasResultsList = append(mv.MeasResultsList, mr)
			}
			mili.MeasValuesList = append(mili.MeasValuesList, mv)
		}

		pmfile.Event.Perf3GppFields.MeasDataCollection.SMeasInfoList = append(pmfile.Event.Perf3GppFields.MeasDataCollection.SMeasInfoList, mili)
	}

	pmfile.Event.Perf3GppFields.MeasDataCollection.GranularityPeriod = 900

	//TODO: Fill more values
	pmfile.Event.CommonEventHeader.Domain = ""    //xfeh.Domain
	pmfile.Event.CommonEventHeader.EventID = ""   //xfeh.EventID
	pmfile.Event.CommonEventHeader.Sequence = 0   //xfeh.Sequence
	pmfile.Event.CommonEventHeader.EventName = "" //xfeh.EventName
	pmfile.Event.CommonEventHeader.SourceName = xfeh.SourceName
	pmfile.Event.CommonEventHeader.ReportingEntityName = "" //xfeh.ReportingEntityName
	pmfile.Event.CommonEventHeader.Priority = ""            //xfeh.Priority
	pmfile.Event.CommonEventHeader.StartEpochMicrosec = xfeh.StartEpochMicrosec
	pmfile.Event.CommonEventHeader.LastEpochMicrosec = xfeh.LastEpochMicrosec
	pmfile.Event.CommonEventHeader.Version = ""                 //xfeh.Version
	pmfile.Event.CommonEventHeader.VesEventListenerVersion = "" //xfeh.VesEventListenerVersion
	pmfile.Event.CommonEventHeader.TimeZoneOffset = xfeh.TimeZoneOffset

	log.Debug("Convert xml to json : ", time.Since(start).String())

	start = time.Now()
	json, err := jsoniter.Marshal(pmfile)
	log.Debug("Marshal json : ", time.Since(start).String())

	if err != nil {
		return nil, errors.New("Cannot marshal converted json")
	}
	return json, nil
}

func start_job_json_file_data(type_id string, control_ch chan JobControl, data_in_ch chan *KafkaPayload, data_out_channel chan *KafkaPayload, objectstore bool) {

	log.Info("Type job", type_id, " started")

	filters := make(map[string]Filter)
	filterParams_list := make(map[string]FilterMaps)
	topic_list := make(map[string]string)
	var mc *minio.Client
	const mc_id = "mc_" + "start_job_json_file_data"
	running := true
	for {
		select {
		case job_ctl := <-control_ch:
			log.Debug("Type job ", type_id, " new cmd received ", job_ctl.command)
			switch job_ctl.command {
			case "EXIT":
			case "ADD-FILTER":
				filters[job_ctl.filter.JobId] = job_ctl.filter
				log.Debug("Type job ", type_id, " updated, topic: ", job_ctl.filter.OutputTopic, " jobid: ", job_ctl.filter.JobId)

				tmp_filterParams_list := make(map[string]FilterMaps)
				for k, v := range filterParams_list {
					tmp_filterParams_list[k] = v
				}
				tmp_filterParams_list[job_ctl.filter.JobId] = job_ctl.filter.filter
				filterParams_list = tmp_filterParams_list

				tmp_topic_list := make(map[string]string)
				for k, v := range topic_list {
					tmp_topic_list[k] = v
				}
				tmp_topic_list[job_ctl.filter.JobId] = job_ctl.filter.OutputTopic
				topic_list = tmp_topic_list
			case "REMOVE-FILTER":
				log.Debug("Type job ", type_id, " removing job: ", job_ctl.filter.JobId)

				tmp_filterParams_list := make(map[string]FilterMaps)
				for k, v := range filterParams_list {
					tmp_filterParams_list[k] = v
				}
				delete(tmp_filterParams_list, job_ctl.filter.JobId)
				filterParams_list = tmp_filterParams_list

				tmp_topic_list := make(map[string]string)
				for k, v := range topic_list {
					tmp_topic_list[k] = v
				}
				delete(tmp_topic_list, job_ctl.filter.JobId)
				topic_list = tmp_topic_list
			}

		case msg := <-data_in_ch:
			if msg == nil {
				log.Info("Type job ", type_id, " stopped by channel signal -  start_job_xml_file_data")

				running = false
				return
			}
			if objectstore {
				if mc == nil {
					var err *error
					mc, err = create_minio_client(mc_id)
					if err != nil {
						log.Debug("Cannot create minio client for type job: ", type_id)
					}
				}
			}
			//TODO: Sort processed file conversions in order (FIFO)
			jobLimiterChan <- struct{}{}
			go run_json_job(type_id, msg, "", filterParams_list, topic_list, data_out_channel, jobLimiterChan, mc, mc_id, objectstore)

		case <-time.After(1 * time.Second):
			if !running {
				return
			}
		}
	}
}

func run_json_job(type_id string, msg *KafkaPayload, outputCompression string, filterList map[string]FilterMaps, topic_list map[string]string, data_out_channel chan *KafkaPayload, jobLimiterChan chan struct{}, mc *minio.Client, mc_id string, objectstore bool) {

	//Release job limit
	defer func() {
		<-jobLimiterChan
	}()

	PrintMemUsage()

	var evt_data FileDownloadedEvt
	err := jsoniter.Unmarshal(msg.msg.Value, &evt_data)
	if err != nil {
		log.Error("Cannot parse FileDownloadedEvt for type job: ", type_id, " - discarding message, error details: ", err)
		return
	}
	log.Debug("FileDownloadedEvt for job: ", type_id, " file: ", evt_data.Filename)

	var reader io.Reader

	INPUTBUCKET := "pm-files-json"
	filename := ""
	if objectstore == false {
		filename = files_volume + "/" + evt_data.Filename
		fi, err := os.Open(filename)

		if err != nil {
			log.Error("File: ", filename, "for type job: ", type_id, " - cannot be opened -discarding message - error details: ", err)
			return
		}
		defer fi.Close()
		reader = fi
	} else {
		filename = "/" + evt_data.Filename
		if mc != nil {
			if check_minio_bucket(mc, mc_id, INPUTBUCKET) == false {
				log.Error("Bucket not available for reading in type job: ", type_id, "bucket: ", INPUTBUCKET)
				return
			}
			tctx := context.Background()
			mr, err := mc.GetObject(tctx, INPUTBUCKET, filename, minio.GetObjectOptions{})
			if err != nil {
				log.Error("Obj: ", filename, "for type job: ", type_id, " - cannot be fetched from bucket: ", INPUTBUCKET, " - discarding message, error details: ", err)
				return
			}
			reader = mr
			defer mr.Close()
		} else {
			log.Error("Cannot get obj: ", filename, "for type job: ", type_id, " from bucket: ", INPUTBUCKET, " - no client")
			return
		}
	}

	var data *[]byte
	if strings.HasSuffix(filename, "gz") {
		start := time.Now()
		var buf2 bytes.Buffer
		errb := gunzipReaderToWriter(&buf2, reader)
		if errb != nil {
			log.Error("Cannot decompress file/obj ", filename, "for type job: ", type_id, " - discarding message, error details", errb)
			return
		}
		d := buf2.Bytes()
		data = &d
		log.Debug("Decompress file/obj ", filename, "for type job: ", type_id, " time:", time.Since(start).String())
	} else {

		start := time.Now()
		d, err := io.ReadAll(reader)
		if err != nil {
			log.Error("Cannot read file/obj: ", filename, "for type job: ", type_id, " - discarding message, error details", err)
			return
		}
		data = &d

		log.Debug("Read file/obj: ", filename, "for type job: ", type_id, " time: ", time.Since(start).String())
	}

	for k, v := range filterList {

		var pmfile PMJsonFile
		start := time.Now()
		err = jsoniter.Unmarshal(*data, &pmfile)
		log.Debug("Json unmarshal obj: ", filename, " time: ", time.Since(start).String())

		if err != nil {
			log.Error("Msg could not be unmarshalled - discarding message, obj: ", filename, " - error details:", err)
			return
		}

		var kmsg *KafkaPayload = new(KafkaPayload)
		kmsg.msg = new(kafka.Message)
		kmsg.msg.Key = []byte("\"" + pmfile.Event.CommonEventHeader.SourceName + "\"")
		log.Debug("topic:", topic_list[k])
		log.Debug("sourceNameMap:", v.sourceNameMap)
		log.Debug("measObjClassMap:", v.measObjClassMap)
		log.Debug("measObjInstIdsMap:", v.measObjInstIdsMap)
		log.Debug("measTypesMap:", v.measTypesMap)

		b := json_pm_filter_to_byte(evt_data.Filename, &pmfile, v.sourceNameMap, v.measObjClassMap, v.measObjInstIdsMap, v.measTypesMap)
		if b == nil {
			log.Info("File/obj ", filename, "for job: ", k, " - empty after filering, discarding message ")
			return
		}
		kmsg.msg.Value = *b

		kmsg.topic = topic_list[k]
		kmsg.jobid = k

		data_out_channel <- kmsg
	}

}

func json_pm_filter_to_byte(resource string, data *PMJsonFile, sourceNameMap map[string]bool, measObjClassMap map[string]bool, measObjInstIdsMap map[string]bool, measTypesMap map[string]bool) *[]byte {

	if json_pm_filter_to_obj(resource, data, sourceNameMap, measObjClassMap, measObjInstIdsMap, measTypesMap) == nil {
		return nil
	}
	start := time.Now()
	j, err := jsoniter.Marshal(&data)

	log.Debug("Json marshal obj: ", resource, " time: ", time.Since(start).String())

	if err != nil {
		log.Error("Msg could not be marshalled discarding message, obj: ", resource, ", error details: ", err)
		return nil
	}

	log.Debug("Filtered json obj: ", resource, " len: ", len(j))
	return &j
}

func json_pm_filter_to_obj(resource string, data *PMJsonFile, sourceNameMap map[string]bool, measObjClassMap map[string]bool, measObjInstIdsMap map[string]bool, measTypesMap map[string]bool) *PMJsonFile {
	filter_req := true
	start := time.Now()
	if len(sourceNameMap) != 0 {
		if !sourceNameMap[data.Event.CommonEventHeader.SourceName] {
			filter_req = false
			return nil
		}
	}
	if filter_req {
		modified := false
		var temp_mil []MeasInfoList
		for _, zz := range data.Event.Perf3GppFields.MeasDataCollection.SMeasInfoList {

			check_cntr := false
			var cnt_flags []bool
			if len(measTypesMap) > 0 {
				c_cntr := 0
				var temp_mtl []string
				for _, v := range zz.MeasTypes.SMeasTypesList {
					if measTypesMap[v] {
						cnt_flags = append(cnt_flags, true)
						c_cntr++
						temp_mtl = append(temp_mtl, v)
					} else {
						cnt_flags = append(cnt_flags, false)
					}
				}
				if c_cntr > 0 {
					check_cntr = true
					zz.MeasTypes.SMeasTypesList = temp_mtl
				} else {
					modified = true
					continue
				}
			}
			keep := false
			var temp_mvl []MeasValues
			for _, yy := range zz.MeasValuesList {
				keep_class := false
				keep_inst := false
				keep_cntr := false

				dna := strings.Split(yy.MeasObjInstID, ",")
				instName := dna[len(dna)-1]
				cls := strings.Split(dna[len(dna)-1], "=")[0]

				if len(measObjClassMap) > 0 {
					if measObjClassMap[cls] {
						keep_class = true
					}
				} else {
					keep_class = true
				}

				if len(measObjInstIdsMap) > 0 {
					if measObjInstIdsMap[instName] {
						keep_inst = true
					}
				} else {
					keep_inst = true
				}

				if check_cntr {
					var temp_mrl []MeasResults
					cnt_p := 1
					for _, v := range yy.MeasResultsList {
						if cnt_flags[v.P-1] {
							v.P = cnt_p
							cnt_p++
							temp_mrl = append(temp_mrl, v)
						}
					}
					yy.MeasResultsList = temp_mrl
					keep_cntr = true
				} else {
					keep_cntr = true
				}
				if keep_class && keep_cntr && keep_inst {
					keep = true
					temp_mvl = append(temp_mvl, yy)
				}
			}
			if keep {
				zz.MeasValuesList = temp_mvl
				temp_mil = append(temp_mil, zz)
				modified = true
			}

		}
		//Only if modified
		if modified {
			if len(temp_mil) == 0 {
				log.Debug("Msg filtered, nothing found, discarding, obj: ", resource)
				return nil
			}
			data.Event.Perf3GppFields.MeasDataCollection.SMeasInfoList = temp_mil
		}
	}
	log.Debug("Filter: ", time.Since(start).String())
	return data
}

func start_job_json_file_data_influx(type_id string, control_ch chan JobControl, data_in_ch chan *KafkaPayload, objectstore bool) {

	log.Info("Type job", type_id, " started")
	log.Debug("influx job ch ", data_in_ch)
	filters := make(map[string]Filter)
	filterParams_list := make(map[string]FilterMaps)
	influx_job_params := make(map[string]InfluxJobParameters)
	var mc *minio.Client
	const mc_id = "mc_" + "start_job_json_file_data_influx"
	running := true
	for {
		select {
		case job_ctl := <-control_ch:
			log.Debug("Type job ", type_id, " new cmd received ", job_ctl.command)
			switch job_ctl.command {
			case "EXIT":
				//ignore cmd - handled by channel signal
			case "ADD-FILTER":

				filters[job_ctl.filter.JobId] = job_ctl.filter
				log.Debug("Type job ", type_id, " updated, topic: ", job_ctl.filter.OutputTopic, " jobid: ", job_ctl.filter.JobId)
				log.Debug(job_ctl.filter)
				tmp_filterParams_list := make(map[string]FilterMaps)
				for k, v := range filterParams_list {
					tmp_filterParams_list[k] = v
				}
				tmp_filterParams_list[job_ctl.filter.JobId] = job_ctl.filter.filter
				filterParams_list = tmp_filterParams_list

				tmp_influx_job_params := make(map[string]InfluxJobParameters)
				for k, v := range influx_job_params {
					tmp_influx_job_params[k] = v
				}
				tmp_influx_job_params[job_ctl.filter.JobId] = job_ctl.filter.influxParameters
				influx_job_params = tmp_influx_job_params

			case "REMOVE-FILTER":

				log.Debug("Type job ", type_id, " removing job: ", job_ctl.filter.JobId)

				tmp_filterParams_list := make(map[string]FilterMaps)
				for k, v := range filterParams_list {
					tmp_filterParams_list[k] = v
				}
				delete(tmp_filterParams_list, job_ctl.filter.JobId)
				filterParams_list = tmp_filterParams_list

				tmp_influx_job_params := make(map[string]InfluxJobParameters)
				for k, v := range influx_job_params {
					tmp_influx_job_params[k] = v
				}
				delete(tmp_influx_job_params, job_ctl.filter.JobId)
				influx_job_params = tmp_influx_job_params
			}

		case msg := <-data_in_ch:
			log.Debug("Data reveived - influx")
			if msg == nil {
				log.Info("Type job ", type_id, " stopped by channel signal -  start_job_xml_file_data")

				running = false
				return
			}
			if objectstore {
				if mc == nil {
					var err *error
					mc, err = create_minio_client(mc_id)
					if err != nil {
						log.Debug("Cannot create minio client for type job: ", type_id)
					}
				}
			}

			jobLimiterChan <- struct{}{}
			go run_json_file_data_job_influx(type_id, msg, filterParams_list, influx_job_params, jobLimiterChan, mc, mc_id, objectstore)

		case <-time.After(1 * time.Second):
			if !running {
				return
			}
		}
	}
}

func run_json_file_data_job_influx(type_id string, msg *KafkaPayload, filterList map[string]FilterMaps, influxList map[string]InfluxJobParameters, jobLimiterChan chan struct{}, mc *minio.Client, mc_id string, objectstore bool) {

	log.Debug("run_json_file_data_job_influx")
	//Release job limit
	defer func() {
		<-jobLimiterChan
	}()

	PrintMemUsage()

	var evt_data FileDownloadedEvt
	err := jsoniter.Unmarshal(msg.msg.Value, &evt_data)
	if err != nil {
		log.Error("Cannot parse FileDownloadedEvt for type job: ", type_id, " - discarding message, error details: ", err)
		return
	}
	log.Debug("FileDownloadedEvt for job: ", type_id, " file: ", evt_data.Filename)

	var reader io.Reader

	INPUTBUCKET := "pm-files-json"
	filename := ""
	if objectstore == false {
		filename = files_volume + "/" + evt_data.Filename
		fi, err := os.Open(filename)

		if err != nil {
			log.Error("File: ", filename, "for type job: ", type_id, " - cannot be opened -discarding message - error details: ", err)
			return
		}
		defer fi.Close()
		reader = fi
	} else {
		filename = "/" + evt_data.Filename
		if mc != nil {
			if check_minio_bucket(mc, mc_id, INPUTBUCKET) == false {
				log.Error("Bucket not available for reading in type job: ", type_id, "bucket: ", INPUTBUCKET)
				return
			}
			tctx := context.Background()
			mr, err := mc.GetObject(tctx, INPUTBUCKET, filename, minio.GetObjectOptions{})
			if err != nil {
				log.Error("Obj: ", filename, "for type job: ", type_id, " - cannot be fetched from bucket: ", INPUTBUCKET, " - discarding message, error details: ", err)
				return
			}
			reader = mr
			defer mr.Close()
		} else {
			log.Error("Cannot get obj: ", filename, "for type job: ", type_id, " from bucket: ", INPUTBUCKET, " - no client")
			return
		}
	}

	var data *[]byte
	if strings.HasSuffix(filename, "gz") {
		start := time.Now()
		var buf2 bytes.Buffer
		errb := gunzipReaderToWriter(&buf2, reader)
		if errb != nil {
			log.Error("Cannot decompress file/obj ", filename, "for type job: ", type_id, " - discarding message, error details", errb)
			return
		}
		d := buf2.Bytes()
		data = &d
		log.Debug("Decompress file/obj ", filename, "for type job: ", type_id, " time:", time.Since(start).String())
	} else {

		start := time.Now()
		d, err := io.ReadAll(reader)
		if err != nil {
			log.Error("Cannot read file/obj: ", filename, "for type job: ", type_id, " - discarding message, error details", err)
			return
		}
		data = &d

		log.Debug("Read file/obj: ", filename, "for type job: ", type_id, " time: ", time.Since(start).String())
	}
	for k, v := range filterList {

		var pmfile PMJsonFile
		start := time.Now()
		err = jsoniter.Unmarshal(*data, &pmfile)
		log.Debug("Json unmarshal obj: ", filename, " time: ", time.Since(start).String())

		if err != nil {
			log.Error("Msg could not be unmarshalled - discarding message, obj: ", filename, " - error details:", err)
			return
		}

		if len(v.sourceNameMap) > 0 || len(v.measObjInstIdsMap) > 0 || len(v.measTypesMap) > 0 || len(v.measObjClassMap) > 0 {
			b := json_pm_filter_to_obj(evt_data.Filename, &pmfile, v.sourceNameMap, v.measObjClassMap, v.measObjInstIdsMap, v.measTypesMap)
			if b == nil {
				log.Info("File/obj ", filename, "for job: ", k, " - empty after filering, discarding message ")
				return
			}

		}
		fluxParms := influxList[k]
		log.Debug("Influxdb params: ", fluxParms)
		client := influxdb2.NewClient(fluxParms.DbUrl, fluxParms.DbToken)
		writeAPI := client.WriteAPIBlocking(fluxParms.DbOrg, fluxParms.DbBucket)

		for _, zz := range pmfile.Event.Perf3GppFields.MeasDataCollection.SMeasInfoList {
			ctr_names := make(map[string]string)
			for cni, cn := range zz.MeasTypes.SMeasTypesList {
				ctr_names[strconv.Itoa(cni+1)] = cn
			}
			for _, xx := range zz.MeasValuesList {
				log.Debug("Measurement: ", xx.MeasObjInstID)
				log.Debug("Suspect flag: ", xx.SuspectFlag)
				p := influxdb2.NewPointWithMeasurement(xx.MeasObjInstID)
				p.AddField("suspectflag", xx.SuspectFlag)
				p.AddField("granularityperiod", pmfile.Event.Perf3GppFields.MeasDataCollection.GranularityPeriod)
				p.AddField("timezone", pmfile.Event.CommonEventHeader.TimeZoneOffset)
				for _, yy := range xx.MeasResultsList {
					pi := strconv.Itoa(yy.P)
					pv := yy.SValue
					pn := ctr_names[pi]
					log.Debug("Counter: ", pn, " Value: ", pv)
					pv_i, err := strconv.Atoi(pv)
					if err == nil {
						p.AddField(pn, pv_i)
					} else {
						p.AddField(pn, pv)
					}
				}
				//p.SetTime(timeT)
				log.Debug("StartEpochMicrosec from common event header:  ", pmfile.Event.CommonEventHeader.StartEpochMicrosec)
				log.Debug("Set time: ", time.Unix(pmfile.Event.CommonEventHeader.StartEpochMicrosec/1000000, 0))
				p.SetTime(time.Unix(pmfile.Event.CommonEventHeader.StartEpochMicrosec/1000000, 0))
				err := writeAPI.WritePoint(context.Background(), p)
				if err != nil {
					log.Error("Db write error: ", err)
				}
			}

		}
		client.Close()
	}

}
