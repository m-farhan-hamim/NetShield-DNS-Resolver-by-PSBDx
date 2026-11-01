package com.example.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.R;
import com.example.util.AppRouting;
import com.example.util.BatteryOptimizationHelper;
import com.example.util.RemoteSyncScheduler;

import java.util.concurrent.ExecutorService;

/**
 * A short in-app tutorial for the new features: one card per feature, each with a "try it" button that
 * opens the real screen, and a Skip button that is always there.
 */
public final class FeatureTour {

    private FeatureTour() {
    }

    public static void show(final Activity activity, final SharedPreferences prefs, final ExecutorService background) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_tour, null);
        final TextView tvStep = view.findViewById(R.id.tv_tour_step);
        final TextView tvTitle = view.findViewById(R.id.tv_tour_title);
        final TextView tvBody = view.findViewById(R.id.tv_tour_body);
        final Button btnAction = view.findViewById(R.id.btn_tour_action);
        final Button btnBack = view.findViewById(R.id.btn_tour_back);
        final Button btnNext = view.findViewById(R.id.btn_tour_next);
        final Button btnSkip = view.findViewById(R.id.btn_tour_skip);

        final String[] titles = activity.getResources().getStringArray(R.array.tour_titles);
        final String[] bodies = activity.getResources().getStringArray(R.array.tour_bodies);
        final String[] actions = activity.getResources().getStringArray(R.array.tour_actions);
        final int count = titles.length;
        final int[] index = {0};

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();

        final Runnable render = new Runnable() {
            @Override
            public void run() {
                int i = index[0];
                tvStep.setText(activity.getString(R.string.wizard_step_format, i + 1, count));
                tvTitle.setText(titles[i]);
                tvBody.setText(bodies[i]);
                btnAction.setText(actions[i]);
                btnBack.setVisibility(i == 0 ? View.INVISIBLE : View.VISIBLE);
                btnNext.setText(i == count - 1 ? R.string.tour_done : R.string.wizard_next);
            }
        };

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (index[0]) {
                    case 0: // app routing
                        AppRoutingDialog.show(activity, prefs, background, AppRouting.Scope.APP_WIDE, null);
                        break;
                    case 1: // remote list sync: turn it on right here
                        RemoteSyncScheduler.setEnabled(activity, true);
                        btnAction.setText(R.string.tour_sync_on);
                        break;
                    case 2: // support and guides
                        SupportOverlay.show(activity);
                        break;
                    case 3: // android 17 battery
                        BatteryOptimizationHelper.showWarning(activity, prefs);
                        break;
                    default: // run the setup wizard again
                        Intent wizard = new Intent(activity, SetupWizardActivity.class);
                        wizard.putExtra(SetupWizardActivity.EXTRA_RERUN, true);
                        activity.startActivity(wizard);
                        dialog.dismiss();
                        break;
                }
            }
        });
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (index[0] > 0) {
                    index[0]--;
                    render.run();
                }
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (index[0] >= count - 1) {
                    dialog.dismiss();
                } else {
                    index[0]++;
                    render.run();
                }
            }
        });
        btnSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        render.run();
        dialog.show();
    }
}
