package kr.ject.naspi.contents.infra;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Cloud Storage 이미지 저장소 ({@code naspi.image.storage: gcs}, 기본값).
 *
 * <p>인증은 Application Default Credentials를 쓴다. GKE에서는 Workload Identity로 연결된 서비스 계정
 * (메타데이터 서버)을 쓰며, 서비스 계정 키 파일은 쓰지 않는다. 키가 없으므로 V4 서명은 IAM Credentials
 * {@code signBlob} API로 하고, 서비스 계정에 {@code roles/iam.serviceAccountTokenCreator}가 필요하다.
 *
 * <p>버킷은 비공개(Public Access Prevention, Uniform Bucket-Level Access)로 두고 서명 URL로만 내려받게 한다.
 */
@Component
@ConditionalOnProperty(name = "naspi.image.storage", havingValue = "gcs", matchIfMissing = true)
class GcsImageStorage implements ImageStorage {

    private final Storage storage;
    private final String bucket;

    @Autowired
    GcsImageStorage(@Value("${naspi.image.gcs.project-id}") String projectId,
                    @Value("${naspi.image.gcs.bucket}") String bucket) {
        this(StorageOptions.newBuilder().setProjectId(projectId).build().getService(), bucket);
    }

    GcsImageStorage(Storage storage, String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        BlobInfo blob = BlobInfo.newBuilder(bucket, objectKey).setContentType(contentType).build();
        try {
            storage.create(blob, content, Storage.BlobTargetOption.doesNotExist());
        } catch (StorageException e) {
            throw new NaspiRuntimeException("GCS 업로드 실패: objectKey=" + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            // 이미 없으면 false를 돌려준다. 결과는 같으므로 성공으로 본다.
            storage.delete(bucket, objectKey);
        } catch (StorageException e) {
            throw new NaspiRuntimeException("GCS 삭제 실패: objectKey=" + objectKey, e);
        }
    }

    @Override
    public String signedUrl(String objectKey, Duration expiration) {
        BlobInfo blob = BlobInfo.newBuilder(bucket, objectKey).build();
        try {
            return storage.signUrl(blob, expiration.toSeconds(), TimeUnit.SECONDS,
                    Storage.SignUrlOption.withV4Signature()).toString();
        } catch (RuntimeException e) {
            // 서명 실패는 StorageException이 아닌 SigningException·IllegalStateException 등으로 온다.
            throw new NaspiRuntimeException("GCS 서명 URL 발급 실패: objectKey=" + objectKey, e);
        }
    }
}
