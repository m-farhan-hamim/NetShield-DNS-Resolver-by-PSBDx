package com.example.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

import com.example.dns.BlocklistManager;
import com.example.util.RemoteSyncScheduler;

/** The daily remote-blocklist sync, started by the system JobScheduler. */
public class RemoteSyncJobService extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        BlocklistManager.getInstance(this).syncRemoteBlocklists(new BlocklistManager.SyncCallback() {
            @Override
            public void onProgress(String message) {
            }

            @Override
            public void onComplete(int totalRules) {
                RemoteSyncScheduler.recordSuccess(RemoteSyncJobService.this, totalRules);
                jobFinished(params, false);
            }

            @Override
            public void onError(String error) {
                RemoteSyncScheduler.notifyResult(RemoteSyncJobService.this, false, error);
                // true = try again later with the system's back-off instead of waiting a whole day
                jobFinished(params, true);
            }
        });
        return true; // work continues in the background until jobFinished is called
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // the system stopped us (for example the network went away): run again later
    }
}
