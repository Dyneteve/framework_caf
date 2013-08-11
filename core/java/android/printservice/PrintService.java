/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.printservice;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.print.IPrinterDiscoverySessionObserver;
import android.print.PrintJobInfo;
import android.print.PrinterId;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * This is the base class for implementing print services. A print service knows
 * how to discover and interact one or more printers via one or more protocols.
 * </p>
 * <h3>Printer discovery</h3>
 * <p>
 * A print service is responsible for discovering printers, adding discovered printers,
 * removing added printers, and updating added printers. When the system is interested
 * in printers managed by your service it will call {@link
 * #onCreatePrinterDiscoverySession()} from which you must return a new {@link
 * PrinterDiscoverySession} instance. The returned session encapsulates the interaction
 * between the system and your service during printer discovery. For description of this
 * interaction refer to the documentation for {@link PrinterDiscoverySession}.
 * </p>
 * <p>
 * For every printer discovery session all printers have to be added since system does
 * not retain printers across sessions. Hence, each printer known to this print service
 * should be added only once during a discovery session. Only an already added printer
 * can be removed or updated. Removed printers can be added again.
 * </p>
 * <h3>Print jobs</h3>
 * <p>
 * When a new print job targeted to a printer managed by this print service is is queued,
 * i.e. ready for processing by the print service, you will receive a call to {@link
 * #onPrintJobQueued(PrintJob)}. The print service may handle the print job immediately
 * or schedule that for an appropriate time in the future. The list of all active print
 * jobs for this service is obtained by calling {@link #getActivePrintJobs()}. Active
 * print jobs are ones that are queued or started.
 * </p>
 * <p>
 * A print service is responsible for setting a print job's state as appropriate
 * while processing it. Initially, a print job is queued, i.e. {@link PrintJob#isQueued()
 * PrintJob.isQueued()} returns true, which means that the document to be printed is
 * spooled by the system and the print service can begin processing it. You can obtain
 * the printed document by calling {@link PrintJob#getDocument() PrintJob.getDocument()}
 * whose data is accessed via {@link PrintDocument#getData() PrintDocument.getData()}.
 * After the print service starts printing the data it should set the print job's
 * state to started by calling {@link PrintJob#start()} after which
 * {@link PrintJob#isStarted() PrintJob.isStarted()} would return true. Upon successful
 * completion, the print job should be marked as completed by calling {@link
 * PrintJob#complete() PrintJob.complete()} after which {@link PrintJob#isCompleted()
 * PrintJob.isCompleted()} would return true. In case of a failure, the print job should
 * be marked as failed by calling {@link PrintJob#fail(CharSequence) PrintJob.fail(
 * CharSequence)} after which {@link PrintJob#isFailed() PrintJob.isFailed()} would
 * return true.
 * </p>
 * <p>
 * If a print job is queued or started and the user requests to cancel it, the print
 * service will receive a call to {@link #onRequestCancelPrintJob(PrintJob)} which
 * requests from the service to do best effort in canceling the job. In case the job
 * is successfully canceled, its state has to be marked as cancelled by calling {@link
 * PrintJob#cancel() PrintJob.cancel()} after which {@link PrintJob#isCancelled()
 * PrintJob.isCacnelled()} would return true.
 * </p>
 * <h3>Lifecycle</h3>
 * <p>
 * The lifecycle of a print service is managed exclusively by the system and follows
 * the established service lifecycle. Additionally, starting or stopping a print service
 * is triggered exclusively by an explicit user action through enabling or disabling it
 * in the device settings. After the system binds to a print service, it calls {@link
 * #onConnected()}. This method can be overriden by clients to perform post binding setup.
 * Also after the system unbinds from a print service, it calls {@link #onDisconnected()}.
 * This method can be overriden by clients to perform post unbinding cleanup. Your should
 * not do any work after the system disconnected from your print service since the
 * service can be killed at any time to reclaim memory. The system will not disconnect
 * from a print service if there are active print jobs for the printers managed by it.
 * </p>
 * <h3>Declaration</h3>
 * <p>
 * A print service is declared as any other service in an AndroidManifest.xml but it must
 * also specify that it handles the {@link android.content.Intent} with action {@link
 * #SERVICE_INTERFACE android.printservice.PrintService}. Failure to declare this intent
 * will cause the system to ignore the print service. Additionally, a print service must
 * request the {@link android.Manifest.permission#BIND_PRINT_SERVICE
 * android.permission.BIND_PRINT_SERVICE} permission to ensure that only the system can
 * bind to it. Failure to declare this intent will cause the system to ignore the print
 * service. Following is an example declaration:
 * </p>
 * <pre>
 * &lt;service android:name=".MyPrintService"
 *         android:permission="android.permission.BIND_PRINT_SERVICE"&gt;
 *     &lt;intent-filter&gt;
 *         &lt;action android:name="android.printservice.PrintService" /&gt;
 *     &lt;/intent-filter&gt;
 *     . . .
 * &lt;/service&gt;
 * </pre>
 * <h3>Configuration</h3>
 * <p>
 * A print service can be configured by specifying an optional settings activity which
 * exposes service specific settings, an optional add printers activity which is used for
 * manual addition of printers, vendor name ,etc. It is a responsibility of the system
 * to launch the settings and add printers activities when appropriate.
 * </p>
 * <p>
 * A print service is configured by providing a {@link #SERVICE_META_DATA meta-data}
 * entry in the manifest when declaring the service. A service declaration with a meta-data
 * tag is presented below:
 * <pre> &lt;service android:name=".MyPrintService"
 *         android:permission="android.permission.BIND_PRINT_SERVICE"&gt;
 *     &lt;intent-filter&gt;
 *         &lt;action android:name="android.printservice.PrintService" /&gt;
 *     &lt;/intent-filter&gt;
 *     &lt;meta-data android:name="android.printservice" android:resource="@xml/printservice" /&gt;
 * &lt;/service&gt;</pre>
 * </p>
 * <p>
 * For more details for how to configure your print service via the meta-data refer to
 * {@link #SERVICE_META_DATA} and <code>&lt;{@link android.R.styleable#PrintService
 * print-service}&gt;</code>.
 * </p>
 */
public abstract class PrintService extends Service {

    private static final String LOG_TAG = "PrintService";

    /**
     * The {@link Intent} action that must be declared as handled by a service
     * in its manifest for the system to recognize it as a print service.
     */
    public static final String SERVICE_INTERFACE = "android.printservice.PrintService";

    /**
     * Name under which a {@link PrintService} component publishes additional information
     * about itself. This meta-data must reference a XML resource containing a <code>
     * &lt;{@link android.R.styleable#PrintService print-service}&gt;</code> tag. This is
     * a sample XML file configuring a print service:
     * <pre> &lt;print-service
     *     android:vendor="SomeVendor"
     *     android:settingsActivity="foo.bar.MySettingsActivity"
     *     andorid:addPrintersActivity="foo.bar.MyAddPrintersActivity."
     *     . . .
     * /&gt;</pre>
     * <p>
     * For detailed configuration options that can be specified via the meta-data
     * refer to {@link android.R.styleable#PrintService android.R.styleable.PrintService}.
     * </p>
     */
    public static final String SERVICE_META_DATA = "android.printservice";

    private final Object mLock = new Object();

    private Handler mHandler;

    private IPrintServiceClient mClient;

    private int mLastSessionId = -1;

    @Override
    protected final void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mHandler = new ServiceHandler(base.getMainLooper());
    }

    /**
     * The system has connected to this service.
     */
    protected void onConnected() {
        /* do nothing */
    }

    /**
     * The system has disconnected from this service.
     */
    protected void onDisconnected() {
        /* do nothing */
    }

    /**
     * Callback asking you to create a new {@link PrinterDiscoverySession}.
     *
     * @see PrinterDiscoverySession
     */
    protected abstract PrinterDiscoverySession onCreatePrinterDiscoverySession();

    /**
     * Called when cancellation of a print job is requested. The service
     * should do best effort to fulfill the request. After the cancellation
     * is performed, the print job should be marked as cancelled state by
     * calling {@link PrintJob#cancel()}.
     *
     * @param printJob The print job to cancel.
     *
     * @see PrintJob#cancel() PrintJob.cancel()
     * @see PrintJob#isCancelled() PrintJob.isCancelled()
     */
    protected abstract void onRequestCancelPrintJob(PrintJob printJob);

    /**
     * Called when there is a queued print job for one of the printers
     * managed by this print service.
     *
     * @param printJob The new queued print job.
     *
     * @see PrintJob#isQueued() PrintJob.isQueued()
     * @see #getActivePrintJobs()
     */
    protected abstract void onPrintJobQueued(PrintJob printJob);

    /**
     * Gets the active print jobs for the printers managed by this service.
     * Active print jobs are ones that are not in a final state, i.e. whose
     * state is queued or started.
     *
     * @return The active print jobs.
     *
     * @see PrintJob#isQueued() PrintJob.isQueued()
     * @see PrintJob#isStarted() PrintJob.isStarted()
     */
    public final List<PrintJob> getActivePrintJobs() {
        final IPrintServiceClient client;
        synchronized (mLock) {
            client = mClient;
        }
        if (client == null) {
            return Collections.emptyList();
        }
        try {
            List<PrintJob> printJobs = null;
            List<PrintJobInfo> printJobInfos = client.getPrintJobInfos();
            if (printJobInfos != null) {
                final int printJobInfoCount = printJobInfos.size();
                printJobs = new ArrayList<PrintJob>(printJobInfoCount);
                for (int i = 0; i < printJobInfoCount; i++) {
                    printJobs.add(new PrintJob(printJobInfos.get(i), client));
                }
            }
            if (printJobs != null) {
                return printJobs;
            }
        } catch (RemoteException re) {
            Log.e(LOG_TAG, "Error calling getPrintJobs()", re);
        }
        return Collections.emptyList();
    }

    /**
     * Generates a global printer id given the printer's locally unique one.
     *
     * @param localId A locally unique id in the context of your print service.
     * @return Global printer id.
     */
    public final PrinterId generatePrinterId(String localId) {
        return new PrinterId(new ComponentName(getPackageName(),
                getClass().getName()), localId);
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return new IPrintService.Stub() {
            @Override
            public void setClient(IPrintServiceClient client) {
                mHandler.obtainMessage(ServiceHandler.MSG_SET_CLEINT, client)
                        .sendToTarget();
            }

            @Override
            public void createPrinterDiscoverySession(IPrinterDiscoverySessionObserver observer) {
                mHandler.obtainMessage(ServiceHandler.MSG_ON_CREATE_PRINTER_DISCOVERY_SESSION,
                        observer).sendToTarget();
            }

            @Override
            public void requestCancelPrintJob(PrintJobInfo printJobInfo) {
                mHandler.obtainMessage(ServiceHandler.MSG_ON_REQUEST_CANCEL_PRINTJOB,
                        printJobInfo).sendToTarget();
            }

            @Override
            public void onPrintJobQueued(PrintJobInfo printJobInfo) {
                mHandler.obtainMessage(ServiceHandler.MSG_ON_PRINTJOB_QUEUED,
                        printJobInfo).sendToTarget();
            }
        };
    }

    private final class ServiceHandler extends Handler {
        public static final int MSG_ON_CREATE_PRINTER_DISCOVERY_SESSION = 1;
        public static final int MSG_ON_PRINTJOB_QUEUED = 2;
        public static final int MSG_ON_REQUEST_CANCEL_PRINTJOB = 3;
        public static final int MSG_SET_CLEINT = 4;

        public ServiceHandler(Looper looper) {
            super(looper, null, true);
        }

        @Override
        public void handleMessage(Message message) {
            final int action = message.what;
            switch (action) {
                case MSG_ON_CREATE_PRINTER_DISCOVERY_SESSION: {
                    IPrinterDiscoverySessionObserver observer =
                            (IPrinterDiscoverySessionObserver) message.obj;
                    PrinterDiscoverySession session = onCreatePrinterDiscoverySession();
                    if (session == null) {
                        throw new NullPointerException("session cannot be null");
                    }
                    synchronized (mLock) {
                        if (session.getId() == mLastSessionId) {
                            throw new IllegalStateException("cannot reuse sessions");
                        }
                        mLastSessionId = session.getId();
                    }
                    session.setObserver(observer);
                } break;

                case MSG_ON_REQUEST_CANCEL_PRINTJOB: {
                    PrintJobInfo printJobInfo = (PrintJobInfo) message.obj;
                    onRequestCancelPrintJob(new PrintJob(printJobInfo, mClient));
                } break;

                case MSG_ON_PRINTJOB_QUEUED: {
                    PrintJobInfo printJobInfo = (PrintJobInfo) message.obj;
                    onPrintJobQueued(new PrintJob(printJobInfo, mClient));
                } break;

                case MSG_SET_CLEINT: {
                    IPrintServiceClient client = (IPrintServiceClient) message.obj;
                    synchronized (mLock) {
                        mClient = client;
                    }
                    if (client != null) {
                        onConnected();
                     } else {
                        onDisconnected();
                    }
                } break;

                default: {
                    throw new IllegalArgumentException("Unknown message: " + action);
                }
            }
        }
    }
}
