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
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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
    Queue<Integer> waveData;
    int maxQueue = 0;
    boolean isPlaying = false;
    private AnimatedExpandableListView listView;
    private AnimatedAdapter adapter;
    private View dim_layer;
    private ImageView indicator;
    private boolean isClick = true;
    private boolean isPlayClick = true;

    public MediaPlayer player;
    public ImageButton mStartStopButton;
    private ImageButton mPrevButton;
    private ImageButton mNextButton;
    public int previousWave = 128;

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
        if(player!=null){
            if(player.isPlaying()){
                player.stop();
            }
            player.release();
        }

        finish();
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

    @Override
    protected void onStop() {
        super.onStop();
        if(player!=null && player.isPlaying()){
            player.stop();
            player.release();
        }
        player =null;

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

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupGoogleAnalytics();
        this.bannersAds = new ArrayList<>();

        new Networking.GetBannerAds(this).execute();

        // Sets up the navigation bar
        waveData = new LinkedList<Integer>();

        setupRecorder();
        createVisualizer();

        mContext = this;
        mActivity = this;
        playPressed = false;
        doneBuffering = false;

        setLastChannelIndex();
        //player = new MediaPlayer();
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
                clickPlayButton();
            }
        });

        mNextButton = (ImageButton) findViewById(R.id.next_station_button);
        mNextButton.setImageDrawable(forwardBackwardsDrawable(true));
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentIndex = (mCurrentIndex + 1) % mStations.length;
                mStartStopButton.setImageDrawable(playDrawable());
                updateDropdownHeader(mCurrentIndex);
                if(playPressed){
                    clickPlayButton();
                }

                doneBuffering = false;
                updateViews();
                clickPlayButton();
            }
        });

        mPrevButton = (ImageButton) findViewById(R.id.previous_station_button);
        mPrevButton.setImageDrawable(forwardBackwardsDrawable(false));
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mCurrentIndex = ((mCurrentIndex - 1) + mStations.length) % mStations.length;
                mStartStopButton.setImageDrawable(playDrawable());
                updateDropdownHeader(mCurrentIndex);
                if(playPressed){
                    clickPlayButton();
                }

                doneBuffering = false;
                updateViews();
                playPressed = false;
                clickPlayButton();
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

    private void clickPlayButton() {

            if (playPressed) {
                mStartStopButton.setImageDrawable(playDrawable());

                if (player != null) {
                    if (player.isPlaying()) {
                        player.stop();

                    }
                    player.release();
                }
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

    private void updateDropdownHeader(int mCurrentIndex) {
        GroupItem currentGroup = adapter.items.get(0);
        switch (mCurrentIndex){
            case 0:
                currentGroup.title = "Classical";
                break;
            case 1:
                currentGroup.title = "Jazz";
                break;
            case 2:
                currentGroup.title = "Reading";
                break;
            case 3:
                currentGroup.title = "Sports";
                break;
        }
        adapter.items.set(0, currentGroup);
        adapter.notifyDataSetChanged();
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
        player.reset();
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

                if(maxQueue == 10){

                    int count = 0;
                    for(Integer x : waveData){
                        if (Math.abs((float)x.intValue())>125){
                            count += 1;
                        }
                    }
                    if(count > 5){
                        intensity = 100f;
                    }
                    else {
                        intensity = ((float) waveform[0] + 128f) / 256;

                    }

                    addQueue(waveform[0]);

                }
                else {
                    addQueue(waveform[0]);
                    intensity = 100f;

                }
                Log.i("Wave intensity",String.valueOf(waveform[0]));



            }

            @Override
            public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {

            }
        }, rate, true, false); // waveform not freq data
        audioOutput.setEnabled(true);
    }
    private void setupDropdown() {
        List<GroupItem> items = getDropdownData();
        adapter = new AnimatedAdapter(this);
        adapter.setData(items);
        listView = (AnimatedExpandableListView) findViewById(R.id.Elist);
        listView.setAdapter(adapter);
        // In order to show animations, we need to use a custom click handler
        // for our ExpandableListView.
        listView.setOnGroupClickListener(getGroupListener());
        updateDropdownHeader(mCurrentIndex);

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
                    GroupItem currentGroup = adapter.items.get(0);
                    currentGroup.title = childtv.getText().toString();
                    adapter.items.set(0, currentGroup);


                    dim_layer.animate().alpha(0f).setDuration(1000).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            dim_layer.setVisibility(View.VISIBLE);
                        }
                    });



                    if(currentGroup.title.compareTo("Classical")==0){
                        mCurrentIndex = 0;
                    }
                    else if (currentGroup.title.compareTo("Jazz")==0){
                        mCurrentIndex = 1;
                    }
                    else if (currentGroup.title.compareTo("Reading")==0){
                        mCurrentIndex = 2;
                    }
                    else if (currentGroup.title.compareTo("Sports")==0){
                        mCurrentIndex = 3;
                    }

                    playPressed = true;
                    clickPlayButton();
                    player.release();
                    playPressed = false;
                    clickPlayButton();
                    updateViews();



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
    private List<GroupItem> getDropdownData() {
        List<GroupItem> items = new ArrayList<GroupItem>();
        GroupItem item = new GroupItem();

        item.title = "Classical";
        ChildItem child1 = new ChildItem();
        child1.title = "Classical";
        item.items.add(child1);
        ChildItem child2 = new ChildItem();
        child2.title = "Jazz";
        item.items.add(child2);
        ChildItem child3 = new ChildItem();
        child3.title = "Reading";
        item.items.add(child3);
        ChildItem child4 = new ChildItem();
        child4.title = "Sports";
        item.items.add(child4);

        items.add(item);

        return items;
    }

    public static class GroupItem {
        String title;
        List<ChildItem> items = new ArrayList<ChildItem>();
    }

    public static class ChildItem {
        String title;

    }

    public static class ChildHolder {
        TextView title;

    }

    public static class GroupHolder {
        TextView title;
    }
    public Context getContext(){
        return this.getBaseContext();
    }

    private class AnimatedAdapter extends AnimatedExpandableListView.AnimatedExpandableListAdapter {
        private LayoutInflater inflater;


        public List<GroupItem> items;

        public AnimatedAdapter(Context context) {
            inflater = LayoutInflater.from(context);
        }

        public void setData(List<GroupItem> items) {
            this.items = items;
        }

        @Override
        public ChildItem getChild(int groupPosition, int childPosition) {
            return items.get(groupPosition).items.get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getRealChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            ChildHolder holder;
            ChildItem item = getChild(groupPosition, childPosition);
            if (convertView == null) {
                holder = new ChildHolder();
                convertView = inflater.inflate(R.layout.list_item, parent, false);
                holder.title = (TextView) convertView.findViewById(R.id.list_item_text);

                convertView.setTag(holder);
            } else {
                holder = (ChildHolder) convertView.getTag();
            }

            holder.title.setText(item.title);

            ScaleAnimation(convertView, item.title);
            return convertView;
        }

        private void ScaleAnimation(View convertView, String childText) {
            if(childText.compareTo("Classical")==0){


                ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1f);
                anim1.setDuration(100);
                convertView.startAnimation(anim1);


            }

            if(childText.compareTo("Jazz")==0){


                ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1f);
                anim1.setDuration(100);
                anim1.setStartOffset(50);
                convertView.startAnimation(anim1);

            }

            if(childText.compareTo("Reading")==0){

                ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1);
                anim1.setDuration(100);
                anim1.setStartOffset(150);
                convertView.startAnimation(anim1);
            }
            if(childText.compareTo("Sports")==0){

                ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1);
                anim1.setDuration(100);
                anim1.setStartOffset(250);
                convertView.startAnimation(anim1);
            }

        }

        @Override
        public int getRealChildrenCount(int groupPosition) {
            return items.get(groupPosition).items.size();
        }

        @Override
        public GroupItem getGroup(int groupPosition) {
            return items.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            return items.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            GroupHolder holder;
            GroupItem item = getGroup(groupPosition);
            if (convertView == null) {
                holder = new GroupHolder();
                convertView = inflater.inflate(R.layout.group_item, parent, false);
                holder.title = (TextView) convertView.findViewById(R.id.listHeader);
                convertView.setTag(holder);
            } else {
                holder = (GroupHolder) convertView.getTag();
            }

            holder.title.setText(item.title);


            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int arg0, int arg1) {
            return true;
        }

    }

    public void addQueue(int x){
        if(maxQueue<10){
            waveData.add(new Integer(x));
            maxQueue += 1;
        }
        else{
            waveData.poll();
            waveData.add(new Integer(x));
        }

    }
}
