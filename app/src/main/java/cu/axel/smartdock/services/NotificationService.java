package cu.axel.smartdock.services;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnHoverListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import cu.axel.smartdock.R;
import cu.axel.smartdock.utils.DeviceUtils;
import cu.axel.smartdock.utils.Utils;
import cu.axel.smartdock.widgets.HoverInterceptorLayout;
import java.util.ArrayList;
import android.widget.ProgressBar;
import android.graphics.Color;
import android.graphics.PorterDuff;
import cu.axel.smartdock.utils.ColorUtils;

public class NotificationService extends NotificationListenerService {
	private WindowManager wm;
	private WindowManager.LayoutParams nLayoutParams,npLayoutParams;
	private HoverInterceptorLayout notificationLayout;
	private TextView notifTitle,notifText;
	private ImageView notifIcon,notifCancelBtn;
	private Handler handler;
    private SharedPreferences sp;
    private DockServiceReceiver dockReceiver;
    private View notificationPanel;
    private ListView notificationsLv;
    private NotificationManager nm;
    private Button cancelAllBtn;

	@Override
	public void onCreate() {
		super.onCreate();

        sp = PreferenceManager.getDefaultSharedPreferences(this);
		wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nLayoutParams = new WindowManager.LayoutParams();
		nLayoutParams.width = 300;
		nLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		nLayoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
		nLayoutParams.format = PixelFormat.TRANSLUCENT;
		nLayoutParams.y = 5;
		nLayoutParams.x = 5;
		nLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |  WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			nLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
		} else
			nLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;

        //Notification panel
        npLayoutParams = new WindowManager.LayoutParams();
        npLayoutParams.width = Utils.dpToPx(this, 400);
        npLayoutParams.height = Utils.dpToPx(this, 400);
        npLayoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        npLayoutParams.y = Utils.dpToPx(this, 60);
        npLayoutParams.x = Utils.dpToPx(this, 2);
        npLayoutParams.format = PixelFormat.TRANSLUCENT;
        npLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |  WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            npLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else
            npLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;

        notificationPanel = LayoutInflater.from(this).inflate(R.layout.notification_panel, null);
        cancelAllBtn = notificationPanel.findViewById(R.id.cancel_all_n_btn);
        notificationsLv = notificationPanel.findViewById(R.id.notification_lv);

		notificationLayout = (HoverInterceptorLayout) LayoutInflater.from(this).inflate(R.layout.notification, null);
		notificationLayout.setVisibility(View.GONE);

		notifTitle = notificationLayout.findViewById(R.id.notif_title_tv);
		notifText = notificationLayout.findViewById(R.id.notif_text_tv);
		notifIcon = notificationLayout.findViewById(R.id.notif_icon_iv);
        notifCancelBtn = notificationLayout.findViewById(R.id.notif_close_btn);

		wm.addView(notificationLayout, nLayoutParams);

		handler = new Handler();
        notificationLayout.setAlpha(0);

        cancelAllBtn.setOnClickListener(new OnClickListener(){

                @Override
                public void onClick(View p1) {
                    cancelAllNotifications();
                }
            });

        notificationLayout.setOnHoverListener(new OnHoverListener(){

                @Override
                public boolean onHover(View p1, MotionEvent p2) {
                    if (p2.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                        notifCancelBtn.setVisibility(View.VISIBLE);
                        handler.removeCallbacksAndMessages(null);
                    } else if (p2.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                        new Handler().postDelayed(new Runnable(){

                                @Override
                                public void run() {
                                    notifCancelBtn.setVisibility(View.INVISIBLE);
                                }
                            }, 200);
                        hideNotification();
                    }
                    return false;
                }
            });

        dockReceiver = new DockServiceReceiver();
        registerReceiver(dockReceiver, new IntentFilter(getPackageName() + ".NOTIFICATION_PANEL"));

	}

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        updateNotificationCount();
        if (Utils.notificationPanelVisible)
            updateNotificationPanel();

    }


	@Override
	public void onNotificationPosted(final StatusBarNotification sbn) {
		super.onNotificationPosted(sbn);

        updateNotificationCount();

        if (Utils.notificationPanelVisible)
            updateNotificationPanel();

		final Notification notification = sbn.getNotification();

		if (sbn.isOngoing() && !PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_ongoing", false)) {} else if (notification.contentView == null && !isBlackListed(sbn.getPackageName())) {
			Bundle extras=notification.extras;

			String notificationTitle = extras.getString(Notification.EXTRA_TITLE);

            CharSequence notificationText = extras.getCharSequence(Notification.EXTRA_TEXT);

            ColorUtils.applySecondaryColor(sp, notifIcon);
            ColorUtils.applyMainColor(sp, notificationLayout);
            ColorUtils.applySecondaryColor(sp, notifCancelBtn);

            try {
                Drawable notificationIcon = getPackageManager().getApplicationIcon(sbn.getPackageName());
                notifIcon.setPadding(12, 12, 12, 12);
                notifIcon.setImageDrawable(notificationIcon);

            } catch (PackageManager.NameNotFoundException e) {}

			notifTitle.setText(notificationTitle);
			notifText.setText(notificationText);

            notifCancelBtn.setOnClickListener(new OnClickListener(){

                    @Override
                    public void onClick(View p1) {
                        notificationLayout.setVisibility(View.GONE);

                        if (sbn.isClearable())
                            cancelNotification(sbn.getKey());
                    }

                });

			notificationLayout.setOnClickListener(new OnClickListener(){

					@Override
					public void onClick(View p1) {
                        notificationLayout.setVisibility(View.GONE);
                        notificationLayout.setAlpha(0);

						PendingIntent intent = notification.contentIntent;
						if (intent != null) {
							try {
								intent.send();
                                if (sbn.isClearable())
                                    cancelNotification(sbn.getKey());
							} catch (PendingIntent.CanceledException e) {}}
					}
				});

            notificationLayout.setOnLongClickListener(new OnLongClickListener(){

                    @Override
                    public boolean onLongClick(View p1) {
                        sp.edit().putString("blocked_notifications", sp.getString("blocked_notifications", "").trim() + " " + sbn.getPackageName()).commit();
                        notificationLayout.setVisibility(View.GONE);
                        notificationLayout.setAlpha(0);
                        Toast.makeText(NotificationService.this, R.string.silenced_notifications, 5000).show();

                        if (sbn.isClearable())
                            cancelNotification(sbn.getKey());
                        return true;
                    }
                });

			notificationLayout.animate()
				.alpha(1)
				.setDuration(300)
				.setInterpolator(new AccelerateDecelerateInterpolator())
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationStart(Animator animation) {
						notificationLayout.setVisibility(View.VISIBLE);
					}
				});

            if (sp.getBoolean("enable_notification_sound", false))
                DeviceUtils.playEventSound(this, "notification_sound");

            hideNotification();
        }
	}

    public void hideNotification() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable(){

                @Override
                public void run() {
                    notificationLayout.animate()
                        .alpha(0)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                notificationLayout.setVisibility(View.GONE);
                                notificationLayout.setAlpha(0);
                            }
                        });

                }
            }, Integer.parseInt(sp.getString("notification_timeout", "5000")));

    }

    public boolean isBlackListed(String packageName) {
        String ignoredPackages = sp.getString("blocked_notifications", "android");
        return ignoredPackages.contains(packageName);
    }

    private void updateNotificationCount() {
        int count = 0;
        int cancelableCount = 0;

        StatusBarNotification[] notifications;
        try {
            notifications = getActiveNotifications();
        } catch (Exception e) {
            notifications = new StatusBarNotification[0];
        }

        if (notifications != null) {
            for (StatusBarNotification notification : notifications) {
                if (notification != null && (notification.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
                    count++;

                    if (notification.isClearable())
                        cancelableCount++;
                }
                if (cancelableCount > 0) {
                    cancelAllBtn.setVisibility(View.VISIBLE);
                } else {
                    cancelAllBtn.setVisibility(View.INVISIBLE);
                }

            }
            sendBroadcast(new Intent(getPackageName() + ".NOTIFICATION_COUNT_CHANGED").putExtra("count", count));
        }

    }
    public void showNotificationPanel() {
        ColorUtils.applyMainColor(sp, notificationPanel);
        wm.addView(notificationPanel, npLayoutParams);

        updateNotificationPanel();

        notificationPanel.setOnTouchListener(new OnTouchListener(){

                @Override
                public boolean onTouch(View p1, MotionEvent p2) {
                    if (p2.getAction() == p2.ACTION_OUTSIDE && (p2.getY() < notificationPanel.getMeasuredHeight() || p2.getX() < notificationPanel.getX())) {
                        hideNotificationPanel();   
                    }
                    return false;
                }
            });

        Utils.notificationPanelVisible = true;
    }

    public void hideNotificationPanel() {
        wm.removeView(notificationPanel);
        Utils.notificationPanelVisible = false;
    }

    public void updateNotificationPanel() {
        notificationsLv.setAdapter(new NotificationAdapter(this, getActiveNotifications()));
    }

    class DockServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context p1, Intent p2) {
            String action = p2.getStringExtra("action");
            if (action.equals("show"))
                showNotificationPanel();
            else
                hideNotificationPanel();
        }

    }
    class NotificationAdapter extends ArrayAdapter<StatusBarNotification> {
        private Context context;

        public NotificationAdapter(Context context, StatusBarNotification[] notifications) {
            super(context, R.layout.notification, notifications);
            this.context = context;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            final StatusBarNotification sbn = getItem(position);
            final Notification notification = sbn.getNotification();

            if (notification.contentView != null) {
                LinearLayout customNotification = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.custom_notification, null);
                View customView = notification.contentView.apply(context, customNotification);
                customNotification.addView(customView);

                customNotification.setOnClickListener(new OnClickListener(){
                        @Override
                        public void onClick(View p1) {
                            if (notification.contentIntent != null) {
                                hideNotificationPanel();
                                try {
                                    notification.contentIntent.send();

                                    if (sbn.isClearable())
                                        cancelNotification(sbn.getKey());
                                } catch (PendingIntent.CanceledException e) {}

                            }
                        }
                    });

                return customNotification;
            }


            //if (convertView == null)
            convertView = LayoutInflater.from(context).inflate(R.layout.notification_white, null);


            TextView notifTitle=convertView.findViewById(R.id.notif_w_title_tv);
            TextView notifText=convertView.findViewById(R.id.notif_w_text_tv);
            ImageView notifIcon = convertView.findViewById(R.id.notif_w_icon_iv);


            Bundle extras=notification.extras;
            String notificationTitle = extras.getString(Notification.EXTRA_TITLE);
            CharSequence notificationText = extras.getCharSequence(Notification.EXTRA_TEXT);
            int progress = extras.getInt(Notification.EXTRA_PROGRESS);

            if (progress != 0) {
                ProgressBar notifPb = convertView.findViewById(R.id.notif_w_pb);
                notifPb.setVisibility(View.VISIBLE);
                notifPb.setProgress(progress);
            }

            notifTitle.setText(notificationTitle);
            notifText.setText(notificationText);

            try {
                Drawable notificationIcon = getPackageManager().getApplicationIcon(sbn.getPackageName());
                notifIcon.setPadding(12, 12, 12, 12);
                notifIcon.setImageDrawable(notificationIcon);

            } catch (PackageManager.NameNotFoundException e) {}

            if (sbn.isClearable()) {
                ImageView notifCancelBtn = convertView.findViewById(R.id.notif_w_close_btn);
                notifCancelBtn.setVisibility(View.VISIBLE);
                notifCancelBtn.setOnClickListener(new OnClickListener(){

                        @Override
                        public void onClick(View p1) {

                            if (sbn.isClearable())
                                cancelNotification(sbn.getKey());
                        }
                    });
            }

            convertView.setOnClickListener(new OnClickListener(){
                    @Override
                    public void onClick(View p1) {
                        if (notification.contentIntent != null) {
                            hideNotificationPanel();
                            try {
                                notification.contentIntent.send();

                                if (sbn.isClearable())
                                    cancelNotification(sbn.getKey());
                            } catch (PendingIntent.CanceledException e) {}

                        }
                    }
                });

            return convertView;
        }


    }
}
