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

package xmltransform

import (
	"bytes"
	"compress/gzip"
	"encoding/xml"
	"errors"
	"fmt"
	jsoniter "github.com/json-iterator/go"
	log "github.com/sirupsen/logrus"
	"io"
	"main/common/dataTypes"
	"net/http"
	"strconv"
	"time"
)

func xml_to_json_conv(f_byteValue *[]byte, xfeh *dataTypes.XmlFileEventHeader) ([]byte, error) {
	var f dataTypes.MeasCollecFile
	start := time.Now()
	err := xml.Unmarshal(*f_byteValue, &f)
	if err != nil {
		return nil, errors.New("Cannot unmarshal xml-file")
	}
	log.Debug("Unmarshal xml file XmlFileEvent: ", time.Since(start).String())

	start = time.Now()
	var pmfile dataTypes.PMJsonFile

	//TODO: Fill in more values
	pmfile.Event.Perf3GppFields.Perf3GppFieldsVersion = "1.0"
	pmfile.Event.Perf3GppFields.MeasDataCollection.GranularityPeriod = 900
	pmfile.Event.Perf3GppFields.MeasDataCollection.MeasuredEntityUserName = ""
	pmfile.Event.Perf3GppFields.MeasDataCollection.MeasuredEntityDn = f.FileHeader.FileSender.LocalDn
	pmfile.Event.Perf3GppFields.MeasDataCollection.MeasuredEntitySoftwareVersion = f.MeasData.ManagedElement.SwVersion

	for _, it := range f.MeasData.MeasInfo {
		var mili dataTypes.MeasInfoList
		mili.MeasInfoID.SMeasInfoID = it.MeasInfoId
		for _, jt := range it.MeasType {
			mili.MeasTypes.SMeasTypesList = append(mili.MeasTypes.SMeasTypesList, jt.Text)
		}
		for _, jt := range it.MeasValue {
			var mv dataTypes.MeasValues
			mv.MeasObjInstID = jt.MeasObjLdn
			mv.SuspectFlag = jt.Suspect
			if jt.Suspect == "" {
				mv.SuspectFlag = "false"
			}
			for _, kt := range jt.R {
				ni, _ := strconv.Atoi(kt.P)
				nv := kt.Text
				mr := dataTypes.MeasResults{ni, nv}
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

func Convert(inputS3Url, compression, xmlFileEventHeader string) []byte {
	evt_data := dataTypes.XmlFileEventHeader{}
	jsoniter.Unmarshal([]byte(xmlFileEventHeader), &evt_data)

	client := new(http.Client)

	request, err := http.NewRequest("GET", inputS3Url, nil)
	request.Header.Add("Accept-Encoding", "gzip")

	response, err := client.Do(request)
	defer response.Body.Close()

	// Check that the server actually sent compressed data
	var reader io.ReadCloser
	switch compression {
	case "gzip", "gz":
		reader, err = gzip.NewReader(response.Body)
		defer reader.Close()
	default:
		reader = response.Body
	}

	var buf3 bytes.Buffer
	_, err2 := io.Copy(&buf3, reader)
	if err2 != nil {
		log.Error("Error reading response, discarding message, ", err)
		return nil
	}
	file_bytes := buf3.Bytes()
	fmt.Println("Converting to XML")
	b, err := xml_to_json_conv(&file_bytes, &evt_data)
	return b
}
