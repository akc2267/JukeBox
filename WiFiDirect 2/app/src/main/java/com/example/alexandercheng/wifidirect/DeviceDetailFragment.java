/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.example.alexandercheng.wifidirect;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.SeekBar;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import android.os.Handler;
import android.os.Message;

import com.example.alexandercheng.wifidirect.DeviceListFragment.DeviceActionListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;
    static boolean isPaused = false;
    SeekBar timeBar;
    TextView timeText, endTime;
    static int vote = 0;
    static HashSet<File> localSongs;
    static String IPADDRESS;


    static ArrayList<File> playlist = new ArrayList<File>();
    static ArrayList<String> playlistTitles = new ArrayList<>();
    static ArrayAdapter<String> arrayAdapter;
    static ServerSocket serverSocket;
    private ListView songList;
    public class OurMediaPlayer implements MediaPlayer.OnCompletionListener {
        MediaPlayer mp;

        @Override
        public void onCompletion(MediaPlayer mp) {
            if (!localSongs.contains(playlist.get(0)))
                playlist.get(0).delete();
            playlist.remove(0);
            playlistTitles.remove(0);
            arrayAdapter.notifyDataSetChanged();

            mp.reset();
            mp.release();
            if (playlist.size() > 0)
                playSong();
        }

        public void playSong() {
            onConnectionInfoAvailable(info);
            setupUI();
            vote = 0;
            mp = MediaPlayer.create(getActivity().getApplicationContext(), Uri.fromFile(playlist.get(0)));
            System.err.println(playlist.get(0).getPath());
            mp.start();
            mp.setOnCompletionListener(this);
        }
    }

    static OurMediaPlayer mp;

    /**
     * Hold a reference to the parent Activity so we can report the
     * task's current progress and results. The Android framework
     * will pass us a reference to the newly created Activity after
     * each configuration change.
     */
    @Override
    public void onAttach(Activity WifiDirectActivity) {

        Log.d("DeviceDetailFragment", "OnAttach called");
        super.onAttach(WifiDirectActivity);
    }
    /**
     * Set the callback to null so we don't accidentally leak the
     * Activity instance.
     */
    @Override
    public void onDetach() {

        Log.d("DeviceDetailFragment", "OnDetach called");
        super.onDetach();
    }

    @Override
    public void onDestroy(){

        Log.d("DeviceDetailFragment", "OnDestroy called");
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (File file : playlist) {
            if (!localSongs.contains(file))
                file.delete();
        }
        mp.mp.stop();
        mp.mp.release();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d("dead", "OnDestroyViewCalled");
    }
//    @Override
//    public void onCreate(Bundle savedInstanceState){
//        Log.d("yo", "OnCreateCalled");
//        // Retain this fragment across configuration changes.
//        setRetainInstance(true);
//        super.onCreate(savedInstanceState);
//    }
@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Log.d("yo", "OnCreateViewCalled");

    mContentView = inflater.inflate(R.layout.device_detail, null);
    ImageButton playpauseButton = (ImageButton) mContentView.findViewById(R.id.PausePlayButton);
    playpauseButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            playPause(v);
        }
    });
    ImageButton skipVoteButton = (ImageButton) mContentView.findViewById(R.id.SkipButton);
    skipVoteButton.setBackground(getResources().getDrawable(R.drawable.skip_forward));
    skipVoteButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            skipVote(v);
        }
    });

    songList = (ListView)mContentView.findViewById(R.id.songList);
    arrayAdapter = new ArrayAdapter<String>(
            getActivity().getApplicationContext(), android.R.layout.simple_list_item_1, playlistTitles
    );
    songList.setAdapter(arrayAdapter);

    timeBar = (SeekBar) mContentView.findViewById(R.id.seekBar);
    timeText = (TextView) mContentView.findViewById(R.id.curTimeText);
    endTime = (TextView) mContentView.findViewById(R.id.timeLeftText);

    mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {


        @Override
        public void onClick(View v) {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            config.wps.setup = WpsInfo.PBC;
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                    "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
            );
            ((DeviceActionListener) getActivity()).connect(config);

        }
    });

    mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
            new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    ((DeviceActionListener) getActivity()).disconnect();
                }
            });

    //search gallery for image to send. change to audio file
    mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
            new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // Allow user to pick an image from Gallery or other
                    // registered apps
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("audio/*");//mp4
                    startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                }
            });

    mContentView.findViewById(R.id.btn_skip).setOnClickListener(
            new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // Allow user to pick an image from Gallery or other
                    // registered apps
                    TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
                    statusText.setText("Sending skip vote");
                    Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                    serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                    serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                            info.groupOwnerAddress.getHostAddress());
                    serviceIntent.putExtra("IPAddress", IPADDRESS);
                    serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
                    getActivity().startService(serviceIntent);
                }
            });
    return mContentView;

}


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
        Log.d("DeviceDetailFragment", "OnCreate called");

        ScheduledExecutorService sec = Executors.newScheduledThreadPool(1);

        sec.scheduleWithFixedDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        monitorHandler.sendMessage(monitorHandler.obtainMessage());
                    }
                },
                200, //initialDelay
                200, //delay
                TimeUnit.MILLISECONDS);

        try {
            serverSocket = new ServerSocket(8988);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");

        mp = new OurMediaPlayer();
        localSongs = new HashSet<>();
        WifiManager wm = (WifiManager) getActivity().getApplicationContext().getSystemService(getActivity().getApplicationContext().WIFI_SERVICE);
        IPADDRESS = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }

    Handler monitorHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            updateBar();
        }

    };

    private void updateBar(){
        if (playlist.size() == 0) {
            return;
        }
        int timeAt = mp.mp.getCurrentPosition() / 1000;
        int timeEnd = (mp.mp.getDuration() / 1000);
        timeBar.setMax(timeEnd);
        timeBar.setProgress(timeAt);
        int timeLeft = timeEnd - timeAt;
        int displaytimeatMin = timeAt / 60;
        int displaytimeatSec = timeAt % 60;
        int displaytimeLeftMin = timeLeft / 60;
        int displaytimeLeftSec = timeLeft % 60;
        timeText.setText(String.valueOf(String.format("%02d", displaytimeatMin) + ":" + String.format("%02d", displaytimeatSec)));
        endTime.setText(String.valueOf(String.format("%02d", displaytimeLeftMin) + ":" + String.format("%02d", displaytimeLeftSec)));
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.d("DeviceDetailFragment", "OnActivityResult called");

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        if (info.isGroupOwner) {
            final File f = new File(Environment.getExternalStorageDirectory() + "/"
                    + getActivity().getApplicationContext().getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                    + ".mp3");

            File dirs = new File(f.getParent());
            if (!dirs.exists())
                dirs.mkdirs();
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                copyFile(getActivity().getApplicationContext().getContentResolver().openInputStream(data.getData()), new FileOutputStream(f));
                playlist.add(f);
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(f.getAbsolutePath());
                String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if (title == null)
                    playlistTitles.add("Unknown Song");
                else
                    playlistTitles.add(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                mmr.release();
                arrayAdapter.notifyDataSetChanged();
                if (playlist.size() == 1)
                    mp.playSong();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        else {
            Uri uri = data.getData();
            TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
            statusText.setText("Sending: " + uri);
            Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);
            Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
            serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
            serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                    info.groupOwnerAddress.getHostAddress());
            serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
            getActivity().startService(serviceIntent);
        }
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {

        Log.d("DeviceDetailFragment", "OnConnectionInfoAvailable called");
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
//        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
//        view.setText(getResources().getString(R.string.group_owner_text)
//                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
//                        : getResources().getString(R.string.no)));
//
//        // InetAddress from WifiP2pInfo struct.
//        view = (TextView) mContentView.findViewById(R.id.device_info);
//        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
                    .execute();

        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {

        Log.d("DeviceDetailFragment", "showDetails called");
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        //TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        //view.setText(device.deviceAddress);
        //view = (TextView) mContentView.findViewById(R.id.device_info);
        //view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {

        Log.d("DeviceDetailFragment", "resetViews called");
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
//        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
//        view.setText(R.string.empty);
//        view = (TextView) mContentView.findViewById(R.id.device_info);
//        view.setText(R.string.empty);
//        view = (TextView) mContentView.findViewById(R.id.group_owner);
//        view.setText(R.string.empty);
//        view = (TextView) mContentView.findViewById(R.id.status_text);
//        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {

                Socket client = serverSocket.accept();
                InputStream inputstream = client.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(inputstream);
                byte[] byte1 = new byte[1];
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                int nRead;
                if ((nRead = bis.read(byte1, 0, 1)) != -1) {
                    buffer.write(byte1, 0, nRead);
                }
                buffer.flush();
                if (buffer.toByteArray()[0] == 49) {
                    System.out.println("RECEIVE SKIP");
                    // add IP Address to list of skips
                    return "1";
                }
                else {
                    Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                    final File f = new File(Environment.getExternalStorageDirectory() + "/"
                            + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                            + ".mp3");

                    File dirs = new File(f.getParent());
                    if (!dirs.exists())
                        dirs.mkdirs();
                    f.createNewFile();

                    Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                    copyFile(bis, new FileOutputStream(f));
                    //serverSocket.close();
                    return f.getAbsolutePath();
                }
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {


            Log.d("DeviceDetailFragment", "OnPostExecute called");
            if (result != null) {
                if (result == "1")
                    skipVoteHelper();
                else {
                    statusText.setText("File copied - " + result);
                    //Intent intent = new Intent();
                    //intent.setAction(Intent.ACTION_VIEW);
                    File song = new File(result);

                    playlist.add(song);
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(song.getAbsolutePath());
                    String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    if (title == null)
                        playlistTitles.add("Unknown Song");
                    else
                        playlistTitles.add(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                    mmr.release();
                    arrayAdapter.notifyDataSetChanged();
                    Log.d("file", "file://" + result);
                    if (playlist.size() == 1)
                        mp.playSong();
                    //Intent intent = new Intent("android.intent.action.MUSIC_PLAYER");
                    //intent.setDataAndType(Uri.parse("file://" + result), "audio/*");
                    //intent.setDataAndType(Uri.fromFile(song), "video/*");
                    // context.startActivity(intent);
                }
            }

        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

    }

    public void setupUI() {
        Log.d("yo", "setupUICalled");
        ImageButton playpauseButton = (ImageButton) mContentView.findViewById(R.id.PausePlayButton);
        playpauseButton.setBackground(getResources().getDrawable(R.drawable.pause));
        playpauseButton.setVisibility(getView().VISIBLE);
        playpauseButton.setClickable(true);
        ImageButton skipButton = (ImageButton) mContentView.findViewById(R.id.SkipButton);
        skipButton.setVisibility(getView().VISIBLE);
        skipButton.setClickable(true);
        timeBar.setVisibility(getView().VISIBLE);
        timeText.setVisibility(getView().VISIBLE);
        endTime.setVisibility(getView().VISIBLE);
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {

        Log.d("DeviceDetailFragment", "copyFile called");
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

    public void playPause(View view) {

        Log.d("DeviceDetailFragment", "playPause called");
        ImageButton playpauseButton = (ImageButton) mContentView.findViewById(R.id.PausePlayButton);
        if (playlist.size() == 0)
            return;
        if (isPaused) {
            mp.mp.start();
            playpauseButton.setBackground(getResources().getDrawable(R.drawable.pause));
            isPaused = false;
        }
        else {
            mp.mp.pause();
            playpauseButton.setBackground(getResources().getDrawable(R.drawable.play));
            isPaused = true;
        }
    }

    public void skipVote(View view) {

        Log.d("DeviceDetailFragment", "skipVote called");
        skipVoteHelper();
    }

    public static void skipVoteHelper() {
        if (playlist.size() == 0 || isPaused)
            return;
        vote++;
        if (vote >= 2) {
            if (!localSongs.contains(playlist.get(0)))
                playlist.get(0).delete();
            playlist.remove(0);
            playlistTitles.remove(0);
            arrayAdapter.notifyDataSetChanged();

            mp.mp.reset();
            mp.mp.release();
            if (playlist.size() > 0)
                mp.playSong();
            vote = 0;
        }
    }

}
