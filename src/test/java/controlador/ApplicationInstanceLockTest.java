package controlador;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationInstanceLockTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearAppRootOverride() {
        System.clearProperty(AppPaths.APP_ROOT_PROPERTY);
    }

    @Test
    void blocksSecondLockUntilFirstIsClosed() throws Exception {
        System.setProperty(AppPaths.APP_ROOT_PROPERTY, tempDir.resolve("app-root").toString());

        ApplicationInstanceLock firstLock = ApplicationInstanceLock.tryAcquire();
        assertThat(firstLock).isNotNull();
        try {
            assertThat(ApplicationInstanceLock.tryAcquire()).isNull();
        } finally {
            firstLock.close();
        }

        ApplicationInstanceLock nextLock = ApplicationInstanceLock.tryAcquire();
        assertThat(nextLock).isNotNull();
        nextLock.close();
    }

    @Test
    void sendsFocusRequestToFirstInstance() throws Exception {
        System.setProperty(AppPaths.APP_ROOT_PROPERTY, tempDir.resolve("app-root").toString());
        CountDownLatch focusRequested = new CountDownLatch(1);

        ApplicationInstanceLock firstLock = ApplicationInstanceLock.tryAcquire();
        assertThat(firstLock).isNotNull();
        try {
            firstLock.startHandoffServer(focusRequested::countDown);

            assertThat(ApplicationInstanceLock.tryAcquire()).isNull();
            assertThat(ApplicationInstanceLock.requestExistingInstanceFocus()).isTrue();
            assertThat(focusRequested.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            firstLock.close();
        }
    }
}
