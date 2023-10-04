package miniocollector

import (
	"bytes"
	"context"
	"io"
	"log"
	"os"
	"testing"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/stretchr/testify/assert"
)

func TestMake_minio_bucket(t *testing.T) {

	endpoint := "play.min.io:9000"
	accessKey := "Q3AM3UQ867SPQQA43P2F"
	secretKey := "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG"

	minioClient, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: true,
	})
	if err != nil {
		log.Fatalf("Error creating Minio client: %v", err)
	}

	if _, err := minioClient.ListBuckets(context.Background()); err != nil {
		log.Fatalf("Error connecting to Minio server: %v", err)
	}

	// Create a test bucket.
	bucketName := "my-test-bucket"
	err = createMinioBucket(minioClient, bucketName)
	if err != nil {
		log.Fatalf("Error creating bucket: %v", err)
	} else {
		assert.NoError(t, err)
	}
}

func Test_bucket_cannot_empty(t *testing.T) {

	endpoint := "play.min.io:9000"
	accessKey := "Q3AM3UQ867SPQQA43P2F"
	secretKey := "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG"

	minioClient, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: true,
	})
	if err != nil {
		log.Fatalf("Error creating Minio client: %v", err)
	}

	if _, err := minioClient.ListBuckets(context.Background()); err != nil {
		log.Fatalf("Error connecting to Minio server: %v", err)
	}

	// Create a test bucket.
	bucketName := ""
	err = createMinioBucket(minioClient, bucketName)
	if err != nil {
		assert.Error(t, err)
	} else {
		assert.NoError(t, err)
	}
}

func Test_check_minio_bucket(t *testing.T) {

	endpoint := "play.min.io:9000"
	accessKey := "Q3AM3UQ867SPQQA43P2F"
	secretKey := "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG"

	found := false

	minioClient, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: true,
	})
	if err != nil {
		log.Fatalf("Error creating Minio client: %v", err)
	}

	if _, err := minioClient.ListBuckets(context.Background()); err != nil {
		log.Fatalf("Error connecting to Minio server: %v", err)
	}

	// Create a test bucket.
	bucketName := "my-test-bucket"
	found = checkMinioBucket(minioClient, bucketName)
	if found {
		assert.True(t, found)
	} else {
		assert.False(t, found)
	}
}

func Test_bucket_not_exists(t *testing.T) {

	endpoint := "play.min.io:9000"
	accessKey := "Q3AM3UQ867SPQQA43P2F"
	secretKey := "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG"

	found := false

	minioClient, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: true,
	})
	if err != nil {
		log.Fatalf("Error creating Minio client: %v", err)
	}

	if _, err := minioClient.ListBuckets(context.Background()); err != nil {
		log.Fatalf("Error connecting to Minio server: %v", err)
	}

	// Create a test bucket.
	bucketName := "my-test-bucket-not-exists"
	found = checkMinioBucket(minioClient, bucketName)
	if found {
		assert.True(t, found)
	} else {
		assert.False(t, found)
	}
}

func Test_upload_object(t *testing.T) {

	endpoint := "play.min.io:9000"
	accessKey := "Q3AM3UQ867SPQQA43P2F"
	secretKey := "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG"

	minioClient, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: true,
	})
	if err != nil {
		log.Fatalf("Error creating Minio client: %v", err)
	}

	if _, err := minioClient.ListBuckets(context.Background()); err != nil {
		log.Fatalf("Error connecting to Minio server: %v", err)
	}

	json_filename := "minio_upload_test.json"

	fi, err := os.Open(json_filename)

	if err != nil {
		t.Fatalf("File %s - cannot be opened  - discarding message, error details: %s", json_filename, err.Error())
	}
	defer fi.Close()

	reader := fi

	var buf3 bytes.Buffer
	_, err2 := io.Copy(&buf3, reader)
	if err2 != nil {
		t.Fatalf("File %s - cannot be read, discarding message, %s", json_filename, err.Error())
		return
	}
	file_bytes := buf3.Bytes()

	// Create a test bucket.
	bucketName := "my-test-bucket"
	uploadObject(minioClient, file_bytes, "minio_upload_test.json", bucketName)

	assert.NoError(t, err)

}
