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
package kafkacollector

import (
	"context"
	"fmt"
	"github.com/confluentinc/confluent-kafka-go/kafka"
	jsoniter "github.com/json-iterator/go"
	log "github.com/sirupsen/logrus"
	"golang.org/x/oauth2/clientcredentials"
	"main/common/dataTypes"
        "main/components/miniocollector"
	"os"
	"time"
)

var creds_grant_type = os.Getenv("CREDS_GRANT_TYPE")
var bootstrapserver = os.Getenv("KAFKA_SERVER")
var creds_client_secret = os.Getenv("CREDS_CLIENT_SECRET")
var creds_client_id = os.Getenv("CREDS_CLIENT_ID")
var creds_service_url = os.Getenv("AUTH_SERVICE_URL")

// Limiter - valid for all jobs
const parallelism_limiter = 100 //For all jobs
var jobLimiterChan = make(chan struct{}, parallelism_limiter)

func Start_topic_reader(topic string, type_id string, control_ch chan dataTypes.ReaderControl, data_ch chan *dataTypes.KafkaPayload, gid string, cid string) {

	log.Info("Topic reader starting, topic: ", topic, " for type: ", type_id)

	topic_ok := false
	var c *kafka.Consumer = nil
	running := true

	for topic_ok == false {

		select {
		case reader_ctrl := <-control_ch:
			if reader_ctrl.Command == "EXIT" {
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
					token, err := Fetch_token()
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
					if reader_ctrl.Command == "EXIT" {
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
						var kmsg dataTypes.KafkaPayload
						kmsg.Msg = e

						c.Commit()

						data_ch <- &kmsg
						log.Debug("Reader msg: ", &kmsg)
						log.Debug("Reader - data_ch ", data_ch)
					case kafka.Error:
						fmt.Fprintf(os.Stderr, "%% Error: %v: %v\n", e.Code(), e)

					case kafka.OAuthBearerTokenRefresh:
						log.Debug("New consumer token needed: ", ev)
						token, err := Fetch_token()
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

func Start_topic_writer(control_ch chan dataTypes.WriterControl, data_ch chan *dataTypes.KafkaPayload) {

	var kafka_producer *kafka.Producer

	running := true
	log.Info("Topic writer starting")

	// Wait for kafka producer to become available - and be prepared to exit the writer
	for kafka_producer == nil {
		select {
		case writer_ctl := <-control_ch:
			if writer_ctl.Command == "EXIT" {
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
					token, err := Fetch_token()
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
				if writer_ctl.Command == "EXIT" {
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
						TopicPartition: kafka.TopicPartition{Topic: &kmsg.Topic, Partition: kafka.PartitionAny},
						Value:          kmsg.Msg.Value, Key: kmsg.Msg.Key}, nil)

					if err == nil {
						msg_ok = true
						log.Debug("Topic writer, msg sent ok on topic: ", kmsg.Topic)
					} else {
						log.Info("Topic writer failed to send message on topic: ", kmsg.Topic, " - Retrying. Error details: ", err)
						time.Sleep(time.Duration(retry) * time.Second)
					}
				}
				if !msg_ok {
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

func create_kafka_consumer(type_id string, gid string, cid string) *kafka.Consumer {
	var cm kafka.ConfigMap
	if creds_grant_type == "" {
		log.Info("Creating kafka plain text consumer for type: ", type_id)
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

func Start_job_xml_file_data(type_id string, control_ch chan dataTypes.JobControl, data_in_ch chan *dataTypes.KafkaPayload, data_out_channel chan *dataTypes.KafkaPayload, fvolume string, fsbucket string) {

	log.Info("Type job", type_id, " started")
	topic_list := make(map[string]string)
	topic_list[type_id] = "json-file-ready-kp"
	topic_list["PmData"] = "json-file-ready-kpadp"
	running := true
	for {
		select {
		case job_ctl := <-control_ch:
			log.Debug("Type job ", type_id, " new cmd received ", job_ctl.Command)
			switch job_ctl.Command {
			case "EXIT":
				//ignore cmd - handled by channel signal
			}

		case msg := <-data_in_ch:
			if msg == nil {
				log.Info("Type job ", type_id, " stopped by channel signal -  start_job_xml_file_data")

				running = false
				return
			}
			jobLimiterChan <- struct{}{}
			go run_xml_job(type_id, msg, "gz", data_out_channel, topic_list, jobLimiterChan, fvolume, fsbucket)

		case <-time.After(1 * time.Second):
			if !running {
				return
			}
		}
	}
}

func run_xml_job(type_id string, msg *dataTypes.KafkaPayload, outputCompression string, data_out_channel chan *dataTypes.KafkaPayload, topic_list map[string]string, jobLimiterChan chan struct{}, fvolume string, fsbucket string) {
	defer func() {
		<-jobLimiterChan
	}()
	start := time.Now()
	var evt_data dataTypes.XmlFileEventHeader

	err := jsoniter.Unmarshal(msg.Msg.Value, &evt_data)
	if err != nil {
		log.Error("Cannot parse XmlFileEventHeader for type job: ", type_id, " - discarding message, error details", err)
		return
	}
	log.Debug("Unmarshal file-collect event for type job: ", type_id, " time: ", time.Since(start).String())

	start = time.Now()
	new_fn := miniocollector.Xml_to_json_conv(&evt_data)
	if err != nil {
		log.Error("Cannot convert file ", evt_data.Name, " - discarding message, ", err)
		return
	}
	log.Debug("Converted file to json: ", new_fn, " time", time.Since(start).String())

	var fde dataTypes.FileDownloadedEvt
	fde.Filename = new_fn
	j, err := jsoniter.Marshal(fde)

	if err != nil {
		log.Error("Cannot marshal FileDownloadedEvt - discarding message, ", err)
		return
	}
	msg.Msg.Value = j

	msg.Msg.Key = []byte("\"" + evt_data.SourceName + "\"")
	log.Debug("Marshal file-collect event ", time.Since(start).String())
	log.Debug("Sending file-collect event to output topic(s)", len(topic_list))
	for _, v := range topic_list {
		fmt.Println("Output Topic: " + v)
		var kmsg *dataTypes.KafkaPayload = new(dataTypes.KafkaPayload)
		kmsg.Msg = msg.Msg
		kmsg.Topic = v
		data_out_channel <- kmsg
	}
}

func Fetch_token() (*kafka.OAuthBearerToken, error) {
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
