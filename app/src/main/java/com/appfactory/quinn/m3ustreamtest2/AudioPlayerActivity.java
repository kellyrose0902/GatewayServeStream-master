package com.appfactory.quinn.m3ustreamtest2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.quinn.m3ustreamtest2.R;
import com.github.gfranks.minimal.notification.GFMinimalNotification;
import com.github.gfranks.minimal.notification.GFMinimalNotificationStyle;
import com.github.gfranks.minimal.notification.activity.BaseNotificationActivity;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * http://199.255.3.11:88/broadwave.m3u?src=2&rate=1    Community Radio HD3
 * http://199.255.3.11:88/broadwave.m3u?src=1&rate=1     Jazz HD2
 * http://sportsweb.gtc.edu:8000/Sportsweb.m3u     Sports
 * http://199.255.3.11:88/broadwave.m3u?src=4&rate=1    Reading Service
 * http://media.gtc.edu:8000/stream.m3u     Public Radio HD1
 */
public class AudioPlayerActivity extends BaseNotificationActivity implements MediaPlayer.OnErrorListener {
    public static Context mContext;
    public static AudioPlayerActivity mActivity;
    public String apiKey;
    public ArrayList<Bitmap> bannersAds;

    private AnimatedExpandableListView listView;
    private AnimatedAdapter adapter;
    private View dim_layer;
    private ImageView indicator;
    private boolean isClick = true;

    public MediaPlayer player;
    public ImageButton mStartStopButton;
    private ImageButton mPrevButton;
    private ImageButton mNextButton;


    ExpandableListView expListView;
    List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;
    List<String> musicType = new ArrayList<>();

    private ImageView currentStationBanner;
    public GFMinimalNotification notification;
    public boolean doneBuffering;
    private ImageView bannerView;
    private int bannerIndex = 0;

    private Visualizer audioOutput = null;
    public float intensity = 0;

    public MediaRecorder mRecorder;

    private StationSource []  mStations = {new StationSource("Public", R.string.public_radio_string, "http://media.gtc.edu:8000/stream\n"), //Public Radio
            new StationSource("Jazz",R.string.jazz_station_string, "http://199.255.3.11:88/broadwave.mp3?src=1&rate=1&ref=http%3A%2F%2Fwww.wgtd.org%2Fhd2.asp"),//Jazz
            new StationSource("Reading",R.string.reading_service_string, "http://199.255.3.11:88/broadwave.mp3?src=4&rate=1&ref=http%3A%2F%2Fwww.wgtd.org%2Freading.asp" ), //Reading Service
            new StationSource("Sports", R.string.sports_station_string, "http://sportsweb.gtc.edu:8000/stream")}; //Sports

    private int[] bannerImages = {R.drawable.classical, R.drawable.jazz, R.drawable.reading, R.drawable.sports};
    private String[] channelNames = {"Classical", "Jazz", "Reading Service", "Sports Radio"};
    private int mCurrentIndex;

    public boolean playPressed;
    private boolean preparing;
    public Timer timer;
    public TimerTask task;

    private Timer checkInternetTimer;
    private Timer bannerTimer;
    private TimerTask bannerTask;
    private TimerTask checkInternetTask;
    public boolean internetErrorOccured;

    public static GoogleAnalytics analytics;
    public static Tracker tracker;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        //MenuInflater inflater = getMenuInflater();
        //inflater.inflate(R.menu.menu_audio_player, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        timer.cancel();
        timer = null;
        player.stop();
        System.exit(0);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(timer == null)
        {
            timer.scheduleAtFixedRate(task, 0, 10);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        editor.putInt("Last Channel", mCurrentIndex);
        editor.commit();
    }

    public void setupGoogleAnalytics()
    {
        analytics = GoogleAnalytics.getInstance(this);
        analytics.setLocalDispatchPeriod(1800);

        tracker = analytics.newTracker("UA-63784829-1"); // Replace with actual tracker/property Id
        tracker.enableExceptionReporting(true);
        tracker.enableAdvertisingIdCollection(true);
        tracker.enableAutoActivityTracking(true);
    }

    public void setupInternetCheckTimer()
    {
        checkInternetTimer = new Timer();

        checkInternetTask = new TimerTask() {

            synchronized public void run() {

                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                       if(mActivity.playPressed)
                       {
                           new ConnectionTest(getApplicationContext(), mActivity, false).execute();
                       }
                    }
                });
            }
        };

        checkInternetTimer.scheduleAtFixedRate(checkInternetTask, 0, 5000);
    }

    public void setupBannerTimer()
    {
        bannerTimer = new Timer();

        bannerTask = new TimerTask() {

            synchronized public void run() {

                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(bannersAds.size() > 1)
                        {
                            bannerIndex++;
                            if(bannerIndex == bannersAds.size())
                            {
                                bannerIndex = 0;
                            }
                            bannerView.setImageBitmap(bannersAds.get(bannerIndex));
                        } else
                        {
                            new Networking.GetBannerAds(mActivity).execute();
                        }
                    }
                });
            }
        };

        bannerTimer.scheduleAtFixedRate(bannerTask, 0, 5000);
    }

    public void setupRecorder() // Recorder used by visualizer
    {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile("/dev/null");

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mRecorder.start();
    }

    private void setLastChannelIndex()
    {
        int lastIndex = getPreferences(MODE_PRIVATE).getInt("Last Channel", -1);

        if (lastIndex == -1)
        {
            mCurrentIndex = 0;
        } else {
            mCurrentIndex = lastIndex;
        }
    }
    private void prepareDataList(){
        listDataHeader = new ArrayList<String>();
        listDataChild = new HashMap<String,List<String>>();

        listDataHeader.add("Jazz");
        musicType.add("Jazz");
        musicType.add("Pop");
        musicType.add("Country");
        musicType.add("Metal");
        musicType.add("Rap");

        listDataChild.put(listDataHeader.get(0), musicType);
    }
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupGoogleAnalytics();
        this.bannersAds = new ArrayList<>();

        new Networking.GetBannerAds(this).execute();

        // Sets up the navigation bar


        setupRecorder();
        createVisualizer();

        mContext = this;
        mActivity = this;
        playPressed = false;
        doneBuffering = false;

        setLastChannelIndex();
        setupPlayer();

        setContentView(R.layout.main_act_layout);

        setupDropdown();
        dim_layer = findViewById(R.id.dim);
        dim_layer.setAlpha(0f);

        currentStationBanner = (ImageView)findViewById(R.id.current_station_banner);
        bannerView = (ImageView)findViewById(R.id.bannerView);

        mStartStopButton = (ImageButton) findViewById(R.id.plav_pause_button);

        mStartStopButton.setImageDrawable(playDrawable());

        mStartStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playPressed) {

                    if (player.isPlaying()) {
                        player.stop();
                    }

                    mStartStopButton.setImageDrawable(playDrawable());
                    doneBuffering = false;

                    if (notification != null) {
                        notification.dismiss();
                    }
                } else {
                    setupPlayer();
                    new ConnectionTest(getApplicationContext(), mActivity, true).execute();

                    mStartStopButton.setImageDrawable(pauseDrawable());

                    if (notification != null) {
                        notification.dismiss();
                    }
                    notification = new GFMinimalNotification(mActivity, GFMinimalNotificationStyle.WARNING, "", "Your stream is loading....",
                            0);
                    notification.show(mActivity);
                }
                playPressed = !playPressed;
            }
        });

        mNextButton = (ImageButton) findViewById(R.id.next_station_button);
        mNextButton.setImageDrawable(forwardBackwardsDrawable(true));
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentIndex = (mCurrentIndex + 1) % mStations.length;

                mStartStopButton.setImageDrawable(playDrawable());

                playPressed = false;
                doneBuffering = false;

                if (player.isPlaying()) {
                    player.stop();
                }

                setupPlayer();
                updateViews();
            }
        });

        mPrevButton = (ImageButton) findViewById(R.id.previous_station_button);
        mPrevButton.setImageDrawable(forwardBackwardsDrawable(false));
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mCurrentIndex = ((mCurrentIndex - 1) + mStations.length) % mStations.length;
                mStartStopButton.setImageDrawable(playDrawable());

                playPressed = false;
                doneBuffering = false;

                if (player.isPlaying()) {
                    player.stop();
                }

                setupPlayer();
                updateViews();
            }
        });

        WaveFragment frag = new WaveFragment();
        frag.parent = this;

        getFragmentManager().beginTransaction()
                .add(R.id.wave_container, frag)
                .commit();

        setupInternetCheckTimer();
        setupBannerTimer();


    }

    private Drawable resize(Drawable image, int size) {
        int pixels = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        Bitmap b = ((BitmapDrawable)image).getBitmap();
        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, pixels*size, pixels*size, false);
        return new BitmapDrawable(getResources(), bitmapResized);
    }

    private void updateStationView()
    {
        currentStationBanner.setImageDrawable(
                getContext().getResources().getDrawable(this.bannerImages[this.mCurrentIndex]));
    }



    public void updateViews()
    {
        updateStationView();

    }

    private Drawable playDrawable() {
        Drawable playIcon = getResources().getDrawable(R.drawable.play);
        playIcon = resize(playIcon, 8);
        int iColor = Color.parseColor("#B62455");

        int red = (iColor & 0xFF0000) / 0xFFFF;
        int green = (iColor & 0xFF00) / 0xFF;
        int blue = iColor & 0xFF;

        float[] matrix = {0, 0, 0, 0, red
                , 0, 0, 0, 0, green
                , 0, 0, 0, 0, blue
                , 0, 0, 0, 1, 0};

        ColorFilter colorFilter = new ColorMatrixColorFilter(matrix);

        playIcon.setColorFilter(colorFilter);

        return playIcon;
    }

    private Drawable pauseDrawable() {
        Drawable playIcon = getResources().getDrawable(R.drawable.pause);
        playIcon = resize(playIcon, 8);
        int iColor = Color.parseColor("#B62455");

        int red = (iColor & 0xFF0000) / 0xFFFF;
        int green = (iColor & 0xFF00) / 0xFF;
        int blue = iColor & 0xFF;

        float[] matrix = {0, 0, 0, 0, red
                , 0, 0, 0, 0, green
                , 0, 0, 0, 0, blue
                , 0, 0, 0, 1, 0};

        ColorFilter colorFilter = new ColorMatrixColorFilter(matrix);

        playIcon.setColorFilter(colorFilter);

        return playIcon;
    }

    private Drawable forwardBackwardsDrawable(Boolean forwards)
    {
        Drawable icon;
        if(forwards)
        {
            icon = getResources().getDrawable(R.drawable.forward);
        } else
        {
            icon = getResources().getDrawable(R.drawable.backward);
        }

        icon = resize(icon, 3);
        int iColor = Color.parseColor("#000000");

        int red = (iColor & 0xFF0000) / 0xFFFF;
        int green = (iColor & 0xFF00) / 0xFF;
        int blue = iColor & 0xFF;

        float[] matrix = {0, 0, 0, 0, red
                , 0, 0, 0, 0, green
                , 0, 0, 0, 0, blue
                , 0, 0, 0, 1, 0};

        ColorFilter colorFilter = new ColorMatrixColorFilter(matrix);

        icon.setColorFilter(colorFilter);

        return icon;
    }


    public void setupPlayer()
    {
        player = new MediaPlayer();
        try
        {
            player.setDataSource(mStations[mCurrentIndex].getSource());
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), String.format("%s", mStations[mCurrentIndex].getSource()), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                if (notification != null) {
                    notification.dismiss();
                }

                if (playPressed) {
                    notification = new GFMinimalNotification(mActivity, GFMinimalNotificationStyle.SUCCESS, "", "Your station is playing!");
                    notification.show(mActivity);

                    player.start();
                    doneBuffering = true;
                }
            }
        });

        player.setOnErrorListener(this);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extras) {
        if (playPressed) {
            if (notification != null) {
                notification.dismiss();
            }
            notification = new GFMinimalNotification(mActivity, GFMinimalNotificationStyle.ERROR, "", "There was an error!");
            notification.show(mActivity);

            doneBuffering = false;

            setupPlayer();
            player.prepareAsync();
        }

        return true;
    }

    private void createVisualizer(){
        int rate = Visualizer.getMaxCaptureRate();
        audioOutput = new Visualizer(0); // get output audio stream
        audioOutput.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override
            public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                intensity = ((float) waveform[0] + 128f) / 256;
            }

            @Override
            public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {

            }
        }, rate, true, false); // waveform not freq data
        audioOutput.setEnabled(true);
    }
    private void setupDropdown() {
        List<AnimatedAdapter.GroupItem> items = getDropdownData();
        adapter = new AnimatedAdapter(this);
        adapter.setData(items);
        listView = (AnimatedExpandableListView) findViewById(R.id.Elist);
        listView.setAdapter(adapter);
        // In order to show animations, we need to use a custom click handler
        // for our ExpandableListView.
        listView.setOnGroupClickListener(getGroupListener());

    }
    public ExpandableListView.OnGroupClickListener getGroupListener(){
        return new ExpandableListView.OnGroupClickListener(){
            @Override
            public boolean onGroupClick(ExpandableListView parent, View gv, int groupPosition, long id) {
                indicator = (ImageView) gv.findViewById(R.id.indicator);

                listView.setOnChildClickListener(getChildListenter());
                // We call collapseGroupWithAnimation(int) and
                // expandGroupWithAnimation(int) to animate group
                // expansion/collapse.

                if (listView.isGroupExpanded(groupPosition)) {
                    dim_layer.animate().alpha(0f).setDuration(1000).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            dim_layer.setVisibility(View.VISIBLE);
                        }
                    });

                    Animation rotate = AnimationUtils.loadAnimation(getBaseContext(), R.anim.reverse_rotate_indicator);
                    indicator.startAnimation(rotate);
                    listView.collapseGroupWithAnimation(groupPosition);


                } else {

                    dim_layer.animate().alpha(0.8f).setDuration(500).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            dim_layer.setVisibility(View.VISIBLE);
                        }
                    });


                    Animation rotate = AnimationUtils.loadAnimation(getBaseContext(), R.anim.rotate_indicator);
                    rotate.setFillAfter(true);
                    indicator.startAnimation(rotate);

                    listView.expandGroupWithAnimation(groupPosition);


                }

                isClick = true;


                return true;
            }

        };
    }

    public ExpandableListView.OnChildClickListener getChildListenter(){
        return new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                if(isClick){
                    isClick = false;
                    TextView childtv = (TextView) v.findViewById(R.id.list_item_text);
                    AnimatedAdapter.GroupItem currentGroup = adapter.items.get(0);
                    currentGroup.title = childtv.getText().toString();
                    adapter.items.set(0, currentGroup);


                    dim_layer.animate().alpha(0f).setDuration(1000).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            dim_layer.setVisibility(View.VISIBLE);
                        }
                    });


                }

                View group = adapter.getGroupView(listView,groupPosition);
                ImageView indicator = (ImageView)group.findViewById(R.id.indicator);
                Animation rotate = AnimationUtils.loadAnimation(getBaseContext(), R.anim.reverse_rotate_indicator);
                indicator.startAnimation(rotate);

                listView.collapseGroupWithAnimation(groupPosition);

                return true;

            }
        };
    }
    @NonNull
    private List<AnimatedAdapter.GroupItem> getDropdownData() {
        List<AnimatedAdapter.GroupItem> items = new ArrayList<AnimatedAdapter.GroupItem>();
        AnimatedAdapter.GroupItem item = new AnimatedAdapter.GroupItem();

        item.title = "Jazz";
        AnimatedAdapter.ChildItem child1 = new AnimatedAdapter.ChildItem();
        child1.title = "Jazz";
        item.items.add(child1);
        AnimatedAdapter.ChildItem child2 = new AnimatedAdapter.ChildItem();
        child2.title = "Rock";
        item.items.add(child2);
        AnimatedAdapter.ChildItem child3 = new AnimatedAdapter.ChildItem();
        child3.title = "Country";
        item.items.add(child3);
        AnimatedAdapter.ChildItem child4 = new AnimatedAdapter.ChildItem();
        child4.title = "Indie";
        item.items.add(child4);

        AnimatedAdapter.ChildItem child5 = new AnimatedAdapter.ChildItem();
        child5.title = "Pop";
        item.items.add(child5);
        items.add(item);

        return items;
    }
    public Context getContext(){
        return this.getBaseContext();
    }
}
