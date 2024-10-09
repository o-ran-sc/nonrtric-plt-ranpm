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

//nolint:all
package kafkacollector

import (
	"context"
	"fmt"
	"main/common/dataTypes"
	"main/components/miniocollector"
	"os"
	"time"

	"github.com/confluentinc/confluent-kafka-go/kafka"
	jsoniter "github.com/json-iterator/go"
	log "github.com/sirupsen/logrus"
	"golang.org/x/oauth2/clientcredentials"
)

var creds_grant_type = os.Getenv("CREDS_GRANT_TYPE")
var bootstrapserver = os.Getenv("KAFKA_SERVER")
var creds_client_secret = os.Getenv("CREDS_CLIENT_SECRET")
var creds_client_id = os.Getenv("CREDS_CLIENT_ID")
var creds_service_url = os.Getenv("AUTH_SERVICE_URL")

// Limiter - valid for all jobs
const parallelism_limiter = 100 //For all jobs
var jobLimiterChan = make(chan struct{}, parallelism_limiter)
var spawnNewTopicReaders = false

const typeLabel = " for type: "
const fetchTokenErrorMessage = "Cannot fetch token: "
const setTokenErrorMessage = "Cannot set token: "

//nolint:all
func StartTopicReader(topic string, typeId string, controlCh chan dataTypes.ReaderControl, dataCh chan *dataTypes.KafkaPayload, gid string, cid string) {

	log.Info("Topic reader starting, topic: ", topic, typeLabel, typeId)

	topicOk := false

	running := true
	spawnNewTopicReaders = true
	var c *kafka.Consumer = nil

	for topicOk == false {
		select {
		case readerCtrl := <-controlCh:
			if readerCtrl.Command == "EXIT" {
				log.Info("Topic reader on topic: ", topic, typeLabel, typeId, " - stopped")
				dataCh <- nil //Signal to job handler
				running = false
				return
			}
		case <-time.After(1 * time.Second):
			if !running {
				return
			}
			if c == nil {
				c = createKafkaConsumer(typeId, gid, cid)
				if c == nil {
					log.Info("Cannot start consumer on topic: ", topic, typeLabel, typeId, " - retrying")
				} else {
					log.Info("Consumer started on topic: ", topic, typeLabel, typeId)
				}
			}
			if c != nil && topicOk == false {
				err := c.SubscribeTopics([]string{topic}, nil)
				if err != nil {
					log.Info("Topic reader cannot start subscribing on topic: ", topic, typeLabel, typeId, " - retrying --  error details: ", err)
				} else {
					log.Info("Topic reader subscribing on topic: ", topic, typeLabel, typeId)
					topicOk = true
				}
			}
		}
	}
	log.Info("Topic reader ready on topic: ", topic, typeLabel, typeId)

	spawnNewTopicReaders = false
	var eventChan = make(chan int)
	go func() {
		for {
			//Kill the subroutine when new subroutine gets created
			if spawnNewTopicReaders {
				return
			}

			select {
			case evt := <-c.Events():
				switch evt.(type) {
				case kafka.OAuthBearerTokenRefresh:
					log.Debug("New consumer token needed: ", evt)
					token, err := FetchToken()
					if err != nil {
						log.Warning(fetchTokenErrorMessage, err)
						c.SetOAuthBearerTokenFailure(err.Error())
					} else {
						setTokenError := c.SetOAuthBearerToken(*token)
						if setTokenError != nil {
							log.Warning(setTokenErrorMessage, setTokenError)
							c.SetOAuthBearerTokenFailure(setTokenError.Error())
						}
					}
				default:
					log.Debug("Dumping topic reader event on topic: ", topic, typeLabel, typeId, " evt: ", evt.String())
				}

			case msg := <-eventChan:
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
				//Kill the subroutine when new subroutine gets created
				if spawnNewTopicReaders {
					//Closed the kafka consumer associated with the routine
					c.Close()
					return
				}
				select {
				case readerCtrl := <-controlCh:
					if readerCtrl.Command == "EXIT" {
						eventChan <- 0
						log.Debug("Topic reader on topic: ", topic, typeLabel, typeId, " - stopped")
						dataCh <- nil //Signal to job handler
						defer c.Close()
						return
					}
				default:

					ev := c.Poll(1000)
					if ev == nil {
						log.Debug("Topic Reader for type: ", typeId, "  Nothing to consume on topic: ", topic)
						continue
					}
					switch e := ev.(type) {
					case *kafka.Message:
						var kmsg dataTypes.KafkaPayload
						kmsg.Msg = e

						c.Commit()

						dataCh <- &kmsg
						log.Debug("Reader msg: ", &kmsg)
						log.Debug("Reader - data_ch ", dataCh)
					case kafka.Error:
						fmt.Fprintf(os.Stderr, "%% Error: %v: %v\n", e.Code(), e)

					case kafka.OAuthBearerTokenRefresh:
						log.Debug("New consumer token needed: ", ev)
						token, err := FetchToken()
						if err != nil {
							log.Warning(fetchTokenErrorMessage, err)
							c.SetOAuthBearerTokenFailure(err.Error())
						} else {
							setTokenError := c.SetOAuthBearerToken(*token)
							if setTokenError != nil {
								log.Warning(setTokenErrorMessage, setTokenError)
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

//nolint:all
func StartTopicWriter(controlCh chan dataTypes.WriterControl, dataCh chan *dataTypes.KafkaPayload) {

	var kafkaProducer *kafka.Producer

	running := true
	log.Info("Topic writer starting")

	// Wait for kafka producer to become available - and be prepared to exit the writer
	for kafkaProducer == nil {
		select {
		case writerCtl := <-controlCh:
			if writerCtl.Command == "EXIT" {
				//ignore cmd
			}
		default:
			kafkaProducer = startProducer()
			if kafkaProducer == nil {
				log.Debug("Could not start kafka producer - retrying")
				time.Sleep(1 * time.Second)
			} else {
				log.Debug("Kafka producer started")
			}
		}
	}

	var eventChan = make(chan int)
	go func() {
		for {
			select {
			case evt := <-kafkaProducer.Events():
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
					token, err := FetchToken()
					if err != nil {
						log.Warning(fetchTokenErrorMessage, err)
						kafkaProducer.SetOAuthBearerTokenFailure(err.Error())
					} else {
						setTokenError := kafkaProducer.SetOAuthBearerToken(*token)
						if setTokenError != nil {
							log.Warning(setTokenErrorMessage, setTokenError)
							kafkaProducer.SetOAuthBearerTokenFailure(setTokenError.Error())
						}
					}
				default:
					log.Debug("Dumping topic writer event, unknown: ", evt)
				}

			case msg := <-eventChan:
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
			case writerCtl := <-controlCh:
				if writerCtl.Command == "EXIT" {
					// ignore - wait for channel signal
				}

			case kmsg := <-dataCh:
				if kmsg == nil {
					eventChan <- 0
					log.Info("Topic writer stopped by channel signal - start_topic_writer")
					defer kafkaProducer.Close()
					return
				}

				retries := 10
				msgOk := false
				var err error
				for retry := 1; retry <= retries && msgOk == false; retry++ {
					err = kafkaProducer.Produce(&kafka.Message{
						TopicPartition: kafka.TopicPartition{Topic: &kmsg.Topic, Partition: kafka.PartitionAny},
						Value:          kmsg.Msg.Value, Key: kmsg.Msg.Key}, nil)

					if err == nil {
						msgOk = true
						log.Debug("Topic writer, msg sent ok on topic: ", kmsg.Topic)
					} else {
						log.Info("Topic writer failed to send message on topic: ", kmsg.Topic, " - Retrying. Error details: ", err)
						time.Sleep(time.Duration(retry) * time.Second)
					}
				}
				if !msgOk {
					log.Error("Topic writer failed to send message on topic: ", kmsg.Topic, " - Msg discarded. Error details: ", err)
				}
			case <-time.After(1000 * time.Millisecond):
				if !running {
					return
				}
			}
		}
	}()
}

//nolint:all
func createKafkaConsumer(typeId string, gid string, cid string) *kafka.Consumer {
	var cm kafka.ConfigMap
	if creds_grant_type == "" {
		log.Info("Creating kafka plain text consumer for type: ", typeId)
		cm = kafka.ConfigMap{
			"bootstrap.servers":  bootstrapserver,
			"group.id":           gid,
			"client.id":          cid,
			"auto.offset.reset":  "latest",
			"enable.auto.commit": false,
		}
	} else {
		log.Info("Creating kafka SASL plain text consumer for type: ", typeId)
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
		log.Error("Cannot create kafka consumer for type: ", typeId, ", error details: ", err)
		return nil
	}

	log.Info("Created kafka consumer for type: ", typeId, " OK")
	return c
}

//nolint:all
func startProducer() *kafka.Producer {
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

//nolint:all
func StartJobXmlFileData(typeId string, controlCh chan dataTypes.JobControl, dataInCh chan *dataTypes.KafkaPayload, dataOutChannel chan *dataTypes.KafkaPayload, fvolume string, fsbucket string) {

	log.Info("Type job", typeId, " started")
	topicList := make(map[string]string)
	topicList[typeId] = "json-file-ready-kp"
	topicList["PmData"] = "json-file-ready-kpadp"
	running := true
	for {
		select {
		case jobCtl := <-controlCh:
			log.Debug("Type job ", typeId, " new cmd received ", jobCtl.Command)
			switch jobCtl.Command {
			case "EXIT":
				//ignore cmd - handled by channel signal
			}

		case msg := <-dataInCh:
			if msg == nil {
				log.Info("Type job ", typeId, " stopped by channel signal -  start_job_xml_file_data")

				running = false
				return
			}
			jobLimiterChan <- struct{}{}
			go runXmlJob(typeId, msg, "gz", dataOutChannel, topicList, jobLimiterChan, fvolume, fsbucket)

		case <-time.After(1 * time.Second):
			if !running {
				return
			}
		}
	}
}

//nolint:all
func runXmlJob(typeId string, msg *dataTypes.KafkaPayload, outputCompression string, dataOutChannel chan *dataTypes.KafkaPayload, topicList map[string]string, jobLimiterChan chan struct{}, fvolume string, fsbucket string) {
	defer func() {
		<-jobLimiterChan
	}()
	start := time.Now()
	var evtData dataTypes.XmlFileEventHeader

	err := jsoniter.Unmarshal(msg.Msg.Value, &evtData)
	if err != nil {
		log.Error("Cannot parse XmlFileEventHeader for type job: ", typeId, " - discarding message, error details", err)
		return
	}
	log.Debug("Unmarshal file-collect event for type job: ", typeId, " time: ", time.Since(start).String())

	start = time.Now()
	newFn := miniocollector.XmlToJsonConv(&evtData)
	if err != nil {
		log.Error("Cannot convert file ", evtData.Name, " - discarding message, ", err)
		return
	}
	log.Debug("Converted file to json: ", newFn, " time", time.Since(start).String())

	var fde dataTypes.FileDownloadedEvt
	fde.Filename = newFn
	j, err := jsoniter.Marshal(fde)

	if err != nil {
		log.Error("Cannot marshal FileDownloadedEvt - discarding message, ", err)
		return
	}
	msg.Msg.Value = j

	msg.Msg.Key = []byte("\"" + evtData.SourceName + "\"")
	log.Debug("Marshal file-collect event ", time.Since(start).String())
	log.Debug("Sending file-collect event to output topic(s)", len(topicList))
	for _, v := range topicList {
		fmt.Println("Output Topic: " + v)
		var kmsg *dataTypes.KafkaPayload = new(dataTypes.KafkaPayload)
		kmsg.Msg = msg.Msg
		kmsg.Topic = v
		dataOutChannel <- kmsg
	}
}

//nolint:all
func FetchToken() (*kafka.OAuthBearerToken, error) {
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
