package avalanche.core.channel;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.UUID;

import avalanche.core.ingestion.AvalancheIngestion;
import avalanche.core.ingestion.ServiceCallback;
import avalanche.core.ingestion.http.AvalancheIngestionHttp;
import avalanche.core.ingestion.models.Device;
import avalanche.core.ingestion.models.Log;
import avalanche.core.ingestion.models.LogContainer;
import avalanche.core.ingestion.models.json.DefaultLogSerializer;
import avalanche.core.ingestion.models.json.LogSerializer;
import avalanche.core.ingestion.models.json.MockLog;
import avalanche.core.ingestion.models.json.MockLogFactory;
import avalanche.core.persistence.AvalanchePersistence;
import avalanche.core.utils.AvalancheLog;
import avalanche.core.utils.StorageHelper;

import static avalanche.core.channel.DefaultAvalancheChannel.ANALYTICS_GROUP;
import static avalanche.core.channel.DefaultAvalancheChannel.ERROR_GROUP;
import static avalanche.core.ingestion.models.json.MockLog.MOCK_LOG_TYPE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AvalancheChannelTest {
    private static Context sContext;
    private static Log sDeviceLog;
    private static LogSerializer sLogSerializer;

    @BeforeClass
    public static void setUpBeforeClass() {
        AvalancheLog.setLogLevel(android.util.Log.VERBOSE);

        sContext = InstrumentationRegistry.getTargetContext();
        StorageHelper.initialize(sContext);

        sDeviceLog = new MockLog();
        sDeviceLog.setSid(UUID.randomUUID());
        Device device = new Device();
        device.setSdkVersion("1.2.3");
        device.setModel("S5");
        device.setOemName("HTC");
        device.setOsName("Android");
        device.setOsVersion("4.0.3");
        device.setOsApiLevel(15);
        device.setLocale("en_US");
        device.setTimeZoneOffset(120);
        device.setScreenSize("800x600");
        device.setAppVersion("3.2.1");
        device.setAppBuild("42");
        sDeviceLog.setDevice(device);
        sLogSerializer = new DefaultLogSerializer();
        sLogSerializer.addLogFactory(MOCK_LOG_TYPE, new MockLogFactory());
    }

    @Test
    public void creationWorks() {
        DefaultAvalancheChannel sut = new DefaultAvalancheChannel(sContext, UUID.randomUUID(), sLogSerializer);
        assertNotNull(sut);
    }

    @Test
    public void persistAnalytics() throws AvalanchePersistence.PersistenceException, InterruptedException {
        AvalanchePersistence mockPersistence = mock(AvalanchePersistence.class);

        //Stubbing getLogs so Persistence returns a batchID and adds 5 logs to the list
        //noinspection unchecked
        when(mockPersistence.getLogs(any(String.class), anyInt(), any(ArrayList.class))).then(new Answer<String>() {
            @SuppressWarnings("unchecked")
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[2] instanceof ArrayList) {
                    ArrayList logs = (ArrayList) args[2];
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                }
                return UUID.randomUUID().toString();
            }
        });

        AvalancheIngestionHttp mockIngestion = mock(AvalancheIngestionHttp.class);

        //don't provide a UUID to prevent sending
        @SuppressWarnings("ConstantConditions")
        DefaultAvalancheChannel sut = new DefaultAvalancheChannel(sContext, null, sLogSerializer);
        sut.setPersistence(mockPersistence);
        sut.setIngestion(mockIngestion);

        //Enqueuing 4 events.
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);

        //Check if our counter is equal the number of events.
        assertEquals(4, sut.getAnalyticsCounter());

        //Enqueue another event.
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);

        //The counter should have been 5 now as we didn't sent data.
        assertEquals(5, sut.getAnalyticsCounter());

        //Verifying that 5 items have been persisted.
        verify(mockPersistence, times(5)).putLog(ANALYTICS_GROUP, sDeviceLog);
    }

    @Test
    public void analyticsSuccess() throws AvalanchePersistence.PersistenceException {
        AvalanchePersistence mockPersistence = mock(AvalanchePersistence.class);

        //Stubbing getLogs so Persistence returns a batchID and adds 5 logs to the list
        //noinspection unchecked
        when(mockPersistence.getLogs(any(String.class), anyInt(), any(ArrayList.class))).then(new Answer<String>() {
            @SuppressWarnings("unchecked")
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[2] instanceof ArrayList) {
                    ArrayList logs = (ArrayList) args[2];
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                }
                return UUID.randomUUID().toString();
            }
        });

        AvalancheIngestionHttp mockIngestion = mock(AvalancheIngestionHttp.class);

        DefaultAvalancheChannel sut = new DefaultAvalancheChannel(sContext, UUID.randomUUID(), sLogSerializer);
        sut.setPersistence(mockPersistence);
        sut.setIngestion(mockIngestion);

        when(mockIngestion.sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[3] instanceof ServiceCallback) {
                    ((ServiceCallback) invocation.getArguments()[3]).success();
                }
                return null;
            }
        });

        //Enqueuing 5 events.
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);

        //Verifying that 5 items have been persisted.
        verify(mockPersistence, times(5)).putLog(ANALYTICS_GROUP, sDeviceLog);

        //Verify that we have called sendAsync on the ingestion
        verify(mockIngestion, times(1)).sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class));

        //Verify that we have called deleteLogs on the persistence
        verify(mockPersistence, times(1)).deleteLog(any(String.class), any(String.class));

        //The counter should be 0 now as we sent data.
        assertEquals(0, sut.getAnalyticsCounter());
    }

    @Test
    public void analyticsRecoverable() throws AvalanchePersistence.PersistenceException {
        AvalanchePersistence mockPersistence = mock(AvalanchePersistence.class);

        //Stubbing getLogs so Persistence returns a batchID and adds 5 logs to the list
        //noinspection unchecked
        when(mockPersistence.getLogs(any(String.class), anyInt(), any(ArrayList.class))).then(new Answer<String>() {
            @SuppressWarnings("unchecked")
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[2] instanceof ArrayList) {
                    ArrayList logs = (ArrayList) args[2];
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                    logs.add(sDeviceLog);
                }
                return UUID.randomUUID().toString();
            }
        });

        AvalancheIngestionHttp mockIngestion = mock(AvalancheIngestionHttp.class);

        //don't provide a UUID to prevent sending
        DefaultAvalancheChannel sut = new DefaultAvalancheChannel(sContext, UUID.randomUUID(), sLogSerializer);

        sut.setPersistence(mockPersistence);
        sut.setIngestion(mockIngestion);

        when(mockIngestion.sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[3] instanceof ServiceCallback) {
                    ((ServiceCallback) invocation.getArguments()[3]).failure(new SocketException());
                }
                return null;
            }
        });

        //Enqueuing 5 events.
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        sut.enqueue(sDeviceLog, ANALYTICS_GROUP);

        //Verifying that 5 items have been persisted.
        verify(mockPersistence, times(5)).putLog(ANALYTICS_GROUP, sDeviceLog);

        //Verify that we have called sendAsync on the ingestion
        verify(mockIngestion, times(1)).sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class));

        //Verify that we have not called deleteLogs on the persistence
        verify(mockPersistence, times(0)).deleteLog(any(String.class), any(String.class));

        //Verify that the Channel is disabled
        assertTrue(sut.isDisabled());

        //Enqueuing 20 more events.
        for (int i = 0; i < 20; i++) {
            sut.enqueue(sDeviceLog, ANALYTICS_GROUP);
        }

        //The counter should have been 10 now as we didn't sent data.
        assertEquals(25, sut.getAnalyticsCounter());

        //Using a fresh ingestion object to change our stub to use the analyticsSuccess()-callback
        mockIngestion = mock(AvalancheIngestionHttp.class);
        when(mockIngestion.sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[3] instanceof ServiceCallback) {
                    ((ServiceCallback) invocation.getArguments()[3]).success();
                }
                return null;
            }
        });

        sut.setIngestion(mockIngestion);

        sut.setDisabled(false);
        sut.synchronize();

        //The counter should back to 0 now.
        assertEquals(0, sut.getAnalyticsCounter());

        //Verify that we have called sendAsync on the ingestion 3 times total (1st one failed with recoverable error.
        verify(mockIngestion, times(5)).sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class));

        //Verify that we have called deleteLogs on the persistence
        verify(mockPersistence, times(5)).deleteLog(any(String.class), any(String.class));
    }

    @Test
    public void persistErrorLog() throws AvalanchePersistence.PersistenceException, InterruptedException {
        AvalanchePersistence mockPersistence = mock(AvalanchePersistence.class);

        //Stubbing getLogs so Persistence returns a batchID and adds 5 logs to the list
        //noinspection unchecked
        when(mockPersistence.getLogs(any(String.class), anyInt(), any(ArrayList.class))).then(new Answer<String>() {
            @SuppressWarnings("unchecked")
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[2] instanceof ArrayList) {
                    ArrayList logs = (ArrayList) args[2];
                    logs.add(sDeviceLog);
                }
                return UUID.randomUUID().toString();
            }
        });

        AvalancheIngestionHttp mockIngestion = mock(AvalancheIngestionHttp.class);

        //don't provide a UUID to prevent sending
        @SuppressWarnings("ConstantConditions") DefaultAvalancheChannel sut = new DefaultAvalancheChannel(sContext, null, sLogSerializer);

        sut.setPersistence(mockPersistence);
        sut.setIngestion(mockIngestion);

        //Enqueuing 4 events.
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);

        //Check if our counter is equal the number of events.
        assertEquals(4, sut.getErrorCounter());

        //Enqueue another event.
        sut.enqueue(sDeviceLog, ERROR_GROUP);

        //The counter should have been 5 now as we didn't sent data.
        assertEquals(5, sut.getErrorCounter());

        //Verifying that 5 items have been persisted.
        verify(mockPersistence, times(5)).putLog(ERROR_GROUP, sDeviceLog);
    }

    @Test
    public void errorLogSuccess() throws AvalanchePersistence.PersistenceException {
        AvalanchePersistence mockPersistence = mock(AvalanchePersistence.class);

        //Stubbing getLogs so Persistence returns a batchID and adds 5 logs to the list
        //noinspection unchecked
        when(mockPersistence.getLogs(any(String.class), anyInt(), any(ArrayList.class))).then(new Answer<String>() {
            @SuppressWarnings("unchecked")
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[2] instanceof ArrayList) {
                    ArrayList logs = (ArrayList) args[2];
                    logs.add(sDeviceLog);
                }
                return UUID.randomUUID().toString();
            }
        });

        AvalancheIngestion mockIngestion = mock(AvalancheIngestion.class);

        DefaultAvalancheChannel sut = new DefaultAvalancheChannel(sContext, UUID.randomUUID(), sLogSerializer);
        sut.setPersistence(mockPersistence);
        sut.setIngestion(mockIngestion);

        when(mockIngestion.sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[3] instanceof ServiceCallback) {
                    ((ServiceCallback) invocation.getArguments()[3]).success();
                }
                return null;
            }
        });

        //Enqueuing 5 events.
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);

        //Verifying that 5 items have been persisted.
        verify(mockPersistence, times(5)).putLog(ERROR_GROUP, sDeviceLog);

        //Verify that we have called sendAsync on the ingestion
        verify(mockIngestion, times(5)).sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class));

        //Verify that we have called deleteLogs on the persistence
        verify(mockPersistence, times(5)).deleteLog(any(String.class), any(String.class));

        //The counter should be 0 now as we sent data.
        assertEquals(0, sut.getErrorCounter());
    }

    @Test
    public void errorLogRecoverable() throws AvalanchePersistence.PersistenceException {
        AvalanchePersistence mockPersistence = mock(AvalanchePersistence.class);

        //Stubbing getLogs so Persistence returns a batchID and adds 5 logs to the list
        //noinspection unchecked
        when(mockPersistence.getLogs(any(String.class), anyInt(), any(ArrayList.class))).then(new Answer<String>() {
            @SuppressWarnings("unchecked")
            public String answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[2] instanceof ArrayList) {
                    ArrayList logs = (ArrayList) args[2];
                    logs.add(sDeviceLog);
                }
                return UUID.randomUUID().toString();
            }
        });

        AvalancheIngestion mockIngestion = mock(AvalancheIngestion.class);

        //don't provide a UUID to prevent sending
        DefaultAvalancheChannel sut = new DefaultAvalancheChannel(sContext, UUID.randomUUID(), sLogSerializer);

        sut.setPersistence(mockPersistence);
        sut.setIngestion(mockIngestion);

        when(mockIngestion.sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[3] instanceof ServiceCallback) {
                    ((ServiceCallback) invocation.getArguments()[3]).failure(new SocketException());
                }
                return null;
            }
        });

        //Enqueuing 5 events.
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);
        sut.enqueue(sDeviceLog, ERROR_GROUP);

        //Verifying that 5 items have been persisted.
        verify(mockPersistence, times(5)).putLog(ERROR_GROUP, sDeviceLog);

        //Verify that we have called sendAsync on the ingestion
        verify(mockIngestion, times(5)).sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class));

        //Verify that we have not called deleteLogs on the persistence
        verify(mockPersistence, times(0)).deleteLog(any(String.class), any(String.class));

        //Verify that the Channel is disabled
        assertTrue(sut.isDisabled());

        //Enqueuing 20 more events.
        for (int i = 0; i < 20; i++) {
            sut.enqueue(sDeviceLog, ERROR_GROUP);
        }

        //The counter should have been 10 now as we didn't sent data.
        assertEquals(25, sut.getErrorCounter());

        //Using a fresh ingestion object to change our stub to use the analyticsSuccess()-callback
        mockIngestion = mock(AvalancheIngestion.class);
        when(mockIngestion.sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class))).then(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                if (args[3] instanceof ServiceCallback) {
                    ((ServiceCallback) invocation.getArguments()[3]).success();
                }
                return null;
            }
        });

        sut.setIngestion(mockIngestion);

        sut.setDisabled(false);
        sut.synchronize();

        //The counter should back to 0 now.
        assertEquals(0, sut.getErrorCounter());

        //Verify that we have called sendAsync on the ingestion 3 times total (1st one failed with recoverable error.
        verify(mockIngestion, times(25)).sendAsync(any(UUID.class), any(UUID.class), any(LogContainer.class), any(ServiceCallback.class));

        //Verify that we have called deleteLogs on the persistence
        verify(mockPersistence, times(25)).deleteLog(any(String.class), any(String.class));
    }
}