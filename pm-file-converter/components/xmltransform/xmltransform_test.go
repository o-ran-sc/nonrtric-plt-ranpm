// -
//
//      ========================LICENSE_START=================================
//      O-RAN-SC
//      %%
//      Copyright (C) 2022: Nordix Foundation
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
	"fmt"
	"io"
	"main/common/dataTypes"
	"os"
	"testing"
)

func TestXMLToJSONConv_Success(t *testing.T) {
	// Prepare the input data
	var evt_data dataTypes.XmlFileEventHeader = dataTypes.XmlFileEventHeader{
		ProductName:       "O-RAN SC",
		VendorName:        "Ericsson AB",
		Location:          "Ireland",
		Compression:       "gzip",
		SourceName:        "GNODEB-0",
		FileFormatType:    "xml",
		FileFormatVersion: "1.0",
		Name:              "Sample Event",
		ChangeIdentifier:  "Sample Change ID",
		InternalLocation:  "Sample Internal Location",
		TimeZoneOffset:    "+05:00",
		ObjectStoreBucket: "Sample Bucket",
	}

	filename := "A20230515.0700_0100-0715_0100_GNODEB-0.xml"
	fi, err := os.Open(filename)

	if err != nil {
		t.Fatalf("File %s - cannot be opened  - discarding message, error details: %s", filename, err.Error())
	}
	defer fi.Close()

	reader := fi

	var buf3 bytes.Buffer
	_, err2 := io.Copy(&buf3, reader)
	if err2 != nil {
		t.Fatalf("File %s - cannot be read, discarding message, %s", filename, err.Error())
		return
	}
	file_bytes := buf3.Bytes()
	b, err := xmlToJsonConv(&file_bytes, &evt_data)

	json_filename := "A20230515.0700_0100-0715_0100_GNODEB-0.json"

	err3 := os.WriteFile(json_filename, b, 0644)

	if err3 != nil {
		t.Fatalf("Error writing %s to JSON :, %s ", json_filename, err3.Error())
	}

	if err != nil {
		t.Fatalf("Cannot convert file %s - discarding message, %s", filename, err.Error())
	}

	if len(b) <= 100000 {
		t.Fatalf("Conversion of %s returned %d bytes", filename, len(b))
	}

	fi, err = os.Open(json_filename)

	if err != nil {
		t.Fatalf("File %s - cannot be opened  - discarding message, error details: %s", json_filename, err.Error())
	} else {
		fmt.Println("XML file Converted to JSON")
	}
	defer fi.Close()
}
