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
package miniocollector

import (
	"bytes"
	"compress/gzip"
	"context"
	"fmt"
	jsoniter "github.com/json-iterator/go"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	log "github.com/sirupsen/logrus"
	"io"
	"main/common/dataTypes"
	"main/components/xmltransform"
	"net/url"
	"os"
	"strings"
	"time"
)

func Xml_to_json_conv(evt_data *dataTypes.XmlFileEventHeader) string {
	filestoreUser := os.Getenv("FILESTORE_USER")
	filestorePwd := os.Getenv("FILESTORE_PWD")
	filestoreServer := os.Getenv("FILESTORE_SERVER")

	s3Client, err := minio.New(filestoreServer, &minio.Options{
		Creds:  credentials.NewStaticV4(filestoreUser, filestorePwd, ""),
		Secure: false,
	})
	if err != nil {
		log.Fatalln(err)
	}
	expiry := time.Second * 24 * 60 * 60 // 1 day.
	objectName := evt_data.Name
	bucketName := evt_data.ObjectStoreBucket
	compresion := evt_data.Compression
	reqParams := make(url.Values)

	xmlh, err := jsoniter.Marshal(evt_data)
	if err != nil {
		fmt.Printf("Error: %s", err)
		return ""
	}

	// Generate presigned GET url with lambda function
	presignedURL, err := s3Client.PresignedGetObject(context.Background(), bucketName, objectName, expiry, reqParams)
	if err != nil {
		log.Fatalln(err)
	}
	file_bytes := xmltransform.Convert(presignedURL.String(), compresion, string(xmlh))
	newObjectName := objectName + "kafka-producer-pm-xml2json-0.json.gz"
	var buf bytes.Buffer
	err = gzipWrite(&buf, &file_bytes)
	upload_object(s3Client, buf.Bytes(), newObjectName, "pm-files-json")
	fmt.Println("")

	return newObjectName
}

func upload_object(mc *minio.Client, b []byte, objectName string, fsbucket string) {
	contentType := "application/json"
	if strings.HasSuffix(objectName, ".gz") {
		contentType = "application/gzip"
	}

	// Upload the xml file with PutObject
	r := bytes.NewReader(b)
	tctx := context.Background()
	if check_minio_bucket(mc, fsbucket) == false {
		err := create_minio_bucket(mc, fsbucket)
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
			log.Debug("Successfully uploaded: ", objectName, " of size:", info.Size)
		}
	}
}

func create_minio_bucket(mc *minio.Client, bucket string) error {
	tctx := context.Background()
	err := mc.MakeBucket(tctx, bucket, minio.MakeBucketOptions{})
	if err != nil {
		// Check to see if we already own this bucket (which happens if you run this twice)
		exists, errBucketExists := mc.BucketExists(tctx, bucket)
		if errBucketExists == nil && exists {
			log.Debug("Already own bucket:", bucket)
			return nil
		} else {
			log.Error("Cannot create or check bucket ", bucket, " in minio client", err)
			return err
		}
	}
	log.Debug("Successfully created bucket: ", bucket)
	return nil
}

func check_minio_bucket(mc *minio.Client, bucket string) bool {
	tctx := context.Background()
	exists, err := mc.BucketExists(tctx, bucket)
	if err == nil && exists {
		log.Debug("Already own bucket:", bucket)
		return true
	}
	log.Error("Bucket does not exist, bucket ", bucket, " in minio client", err)
	return false
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
