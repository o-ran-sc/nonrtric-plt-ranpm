// -
//
//      ========================LICENSE_START=================================
//      O-RAN-SC
//      %%
//      Copyright (C) 2023: Nordix Foundation
//      %%
//      Licensed under the Apache License, Version 2.0 (the "License");
//      you may not use this file except in compliance with the License.
//      You may obtain a copy of the License at
//
//           http://www.apache.org/licenses/LICENSE-2.0
//
//      Unless required by applicable law or agreed to in writing, software
//      distributed under the License is distributed on an "AS IS" BASIS,
//      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//      See the License for the specific language governing permissions and
//      limitations under the License.
//      ========================LICENSE_END===================================

package dataTypes

import (
        "encoding/xml"
	"github.com/confluentinc/confluent-kafka-go/kafka"
)

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
	ObjectStoreBucket  string `json:"objectStoreBucket"`
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

type FileDownloadedEvt struct {
	Filename string `json:"filename"`
}

type KafkaPayload struct {
	Msg   *kafka.Message
	Topic string
}

// Type for controlling the topic reader
type ReaderControl struct {
	Command string
}

// Type for controlling the topic writer
type WriterControl struct {
	Command string
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

	Ext_job         *[]byte
	Ext_job_created bool
	Ext_job_id      string
}

// Type for controlling the job
type JobControl struct {
	Command string
	//Filter  Filter
}

type AppStates int64

var InfoTypes DataTypes

// Keep all info type jobs, key == type id
var TypeJobs map[string]TypeJobRecord = make(map[string]TypeJobRecord)

// Type for an infojob
type TypeJobRecord struct {
	InfoType        string
	InputTopic      string
	Data_in_channel chan *KafkaPayload
	Reader_control  chan ReaderControl
	Job_control     chan JobControl
	GroupId         string
	ClientId        string
}

type DataTypes struct {
	ProdDataTypes []DataType `json:"types"`
}
