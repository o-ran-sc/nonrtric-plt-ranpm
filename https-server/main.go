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
	"bufio"
	"bytes"
	"compress/gzip"
	"io"
	"net/http"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"

	log "github.com/sirupsen/logrus"

	"github.com/gorilla/mux"

	"net/http/pprof"
)

//== Constants ==//

const https_port = 443

const shared_files = "/files"
const template_files = "/template-files"

const file_template = "pm-template.xml.gz"

var always_return_file = os.Getenv("ALWAYS_RETURN")
var generated_files_start_time = os.Getenv("GENERATED_FILE_START_TIME")
var generated_files_timezone = os.Getenv("GENERATED_FILE_TIMEZONE")

var unzipped_template = ""

// Get static file
// Returned file is based on configuration
// If env ALWAYS_RETURN points to file under "/files", then that file is returned regardless of requested file
// Otherwise the requested file is returned if it exists
// If the requested file has a file name prefix of "NONEXISTING",then just 404 is returned
func files(w http.ResponseWriter, req *http.Request) {
	start := time.Now()
	if req.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}

	vars := mux.Vars(req)

	if id, ok := vars["fileid"]; ok {
		if strings.HasPrefix(id, "NONEXISTING") {
			w.WriteHeader(http.StatusNotFound)
		}
		fn := shared_files + "/" + id
		if always_return_file != "" {
			fn = always_return_file
		}
		fileBytes, err := os.ReadFile(fn)
		if err != nil {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusOK)
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Write(fileBytes)

		log.Info("File retrieval : ", fn, "as ", id, ", time:", time.Since(start).String())
		return
	}

	w.WriteHeader(http.StatusNotFound)
}

// Unzip data from a reader and push to a writer
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

// Zip the contents of a byte buffer and push to a writer
func gzipWrite(w io.Writer, data *[]byte) error {
	gw, err1 := gzip.NewWriterLevel(w, gzip.BestSpeed)

	if err1 != nil {
		return err1
	}
	defer gw.Close()
	_, err2 := gw.Write(*data)
	return err2
}

// Get generated file
// Returns a file generated from a template
// The requested file shall be according to this fileformat:
// A<year><month><day>.<hour><minute><timezone>-<hour><minute><timezone>_<nodename>.xml.gz
// Example: A20230220.1400+0100-1415+0100_GNODEBX-332.xml.gz
// The date and time shall be equal to or later then the date time configured start time in env GENERATED_FILE_START_TIME
// If the requested start time is earlier then the configured starttime, 404 is returned
// The returned file has nodename, start and end time set according to the requested file.
// In addition, the counter values are set to value representing the number of 15 min perioids since the
// configured start time
func generatedfiles(w http.ResponseWriter, req *http.Request) {
	start := time.Now()
	if req.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}

	vars := mux.Vars(req)

	if id, ok := vars["fileid"]; ok {
		if strings.HasPrefix(id, "NONEXISTING") {
			w.WriteHeader(http.StatusNotFound)
		}
		log.Debug("Request generated file:", id)
		timezone := "+0000"
		if generated_files_timezone != "" {
			timezone = generated_files_timezone
			log.Debug("Configured timezone: ", timezone)
		} else {
			log.Debug("Using default timezone: ", timezone)
		}

		fn := template_files + "/" + file_template
		if unzipped_template == "" {

			var buf3 bytes.Buffer
			file, err := os.Open(fn)
			if err != nil {
				log.Error("PM template file", file_template, " does not exist")
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			errb := gunzipReaderToWriter(&buf3, bufio.NewReader(file))
			if errb != nil {
				log.Error("Cannot gunzip file ", file_template, " - ", errb)
				return
			}

			unzipped_template = string(buf3.Bytes())

		}

		//Parse file start date/time
		//Example: 20230220.1300

		layout := "20060102.1504"
		tref, err := time.Parse(layout, generated_files_start_time)
		if err != nil {
			log.Error("Env var GENERATED_FILE_START_TIME cannot be parsed")
			w.WriteHeader(http.StatusInternalServerError)
			return
		}

		//Parse file name start date/time

		ts := id[1:14]
		tcur, err := time.Parse(layout, ts)
		if err != nil {
			log.Error("File start date/time cannot be parsed: ", ts)
			w.WriteHeader(http.StatusInternalServerError)
			return
		}

		// Calculate file index based of reference time
		file_index := (tcur.Unix() - tref.Unix()) / 900
		if file_index < 0 {
			log.Error("File start date/time before value of env var GENERATED_FILE_START_TIME :", generated_files_start_time)
			w.WriteHeader(http.StatusInternalServerError)
			return
		}

		//begintime/endtime format: 2022-04-18T19:00:00+00:00
		begintime := tcur.Format("2006-01-02T15:04") + ":00" + timezone
		d := time.Duration(900 * time.Second)
		tend := tcur.Add(d)
		endtime := tend.Format("2006-01-02T15:04") + ":00" + timezone

		//Extract nodename
		nodename := id[30:]
		nodename = strings.Split(nodename, ".")[0]

		template_string := strings.Clone(unzipped_template)

		log.Debug("Replacing BEGINTIME with: ", begintime)
		log.Debug("Replacing ENDTIME with: ", endtime)
		log.Debug("Replacing CTR_VALUE with: ", strconv.Itoa(int(file_index)))
		log.Debug("Replacing NODE_NAME with: ", nodename)

		template_string = strings.Replace(template_string, "BEGINTIME", begintime, -1)
		template_string = strings.Replace(template_string, "ENDTIME", endtime, -1)

		template_string = strings.Replace(template_string, "CTR_VALUE", strconv.Itoa(int(file_index)), -1)

		template_string = strings.Replace(template_string, "NODE_NAME", nodename, -1)

		b := []byte(template_string)
		var buf bytes.Buffer
		err = gzipWrite(&buf, &b)
		if err != nil {
			log.Error("Cannot gzip file ", id, " - ", err)
			return
		}

		w.WriteHeader(http.StatusOK)
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Write(buf.Bytes())

		log.Info("File retrieval generated file: ", fn, "as ", id, ", time:", time.Since(start).String())
		return
	}

	w.WriteHeader(http.StatusNotFound)
}

// Simple alive check
func alive(w http.ResponseWriter, req *http.Request) {
	//Alive check
}

// == Main ==//
func main() {

	log.SetLevel(log.InfoLevel)
	//log.SetLevel(log.TraceLevel)

	log.Info("Server starting...")

	rtr := mux.NewRouter()
	rtr.HandleFunc("/files/{fileid}", files)
	rtr.HandleFunc("/generatedfiles/{fileid}", generatedfiles)
	rtr.HandleFunc("/", alive)

	rtr.HandleFunc("/custom_debug_path/profile", pprof.Profile)

	http.Handle("/", rtr)

	// Run https
	log.Info("Starting https service...")
	err := http.ListenAndServeTLS(":"+strconv.Itoa(https_port), "certs/server.crt", "certs/server.key", nil)
	if err != nil {
		log.Fatal("Cannot setup listener on https: ", err)
	}

	//Wait until all go routines has exited
	runtime.Goexit()

	log.Warn("main routine exit")
	log.Warn("server i stopping...")
}
