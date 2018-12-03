package com.twilio.chat.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.twilio.chat.Channel;
import com.twilio.chat.Channel.ChannelType;
import com.twilio.chat.ChannelDescriptor;
import com.twilio.chat.ChannelListener;
import com.twilio.chat.Channels;
import com.twilio.chat.CallbackListener;
import com.twilio.chat.StatusListener;
import com.twilio.chat.ChatClientListener;
import com.twilio.chat.Member;
import com.twilio.chat.Message;
import com.twilio.chat.ChatClient;
import com.twilio.chat.ErrorInfo;
import com.twilio.chat.User;
import com.twilio.chat.Paginator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import uk.co.ribot.easyadapter.EasyAdapter;
import org.json.JSONObject;
import org.json.JSONException;

@SuppressLint("InflateParams")
public class ChannelActivity extends Activity implements ChatClientListener
{
    private static final Logger logger = Logger.getLogger(ChannelActivity.class);

    private static final String[] CHANNEL_OPTIONS = { "Join" };
    private static final int JOIN = 0;

    private ListView                         listView;
    private BasicChatClient                  basicClient;
    private Map<String, ChannelModel>        channels = new HashMap<String, ChannelModel>();
    private List<ChannelModel>               adapterContents = new ArrayList<>();
    private EasyAdapter<ChannelModel>        adapter;
    private AlertDialog                      createChannelDialog;
    private Channels                         channelsObject;

    private static final Handler handler = new Handler();
    private AlertDialog          incomingChannelInvite;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        basicClient = TwilioApplication.get().getBasicClient();
        basicClient.getChatClient().setListener(ChannelActivity.this);
        setupListView();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        getChannels();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.channel, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.action_create_public:
                showCreateChannelDialog(ChannelType.PUBLIC);
                break;
            case R.id.action_create_private:
                showCreateChannelDialog(ChannelType.PRIVATE);
                break;
            case R.id.action_create_public_withoptions:
                createChannelWithType(ChannelType.PUBLIC);
                break;
            case R.id.action_create_private_withoptions:
                createChannelWithType(ChannelType.PRIVATE);
                break;
            case R.id.action_search_by_unique_name:
                showSearchChannelDialog();
                break;
            case R.id.action_user_info:
                startActivity(new Intent(getApplicationContext(), UserActivity.class));
                break;
            case R.id.action_logout:
                basicClient.shutdown();
                finish();
                break;
            case R.id.action_unregistercm:
                basicClient.unregisterFcmToken();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createChannelWithType(ChannelType type)
    {
        Random rand = new Random();
        int    value = rand.nextInt(50);

        final JSONObject attrs = new JSONObject();
        try {
            attrs.put("topic", "testing channel creation with options " + value);
        } catch (JSONException xcp) {
            logger.e("JSON exception", xcp);
        }

        String typ = type == ChannelType.PRIVATE ? "Priv" : "Pub";

        basicClient.getChatClient().getChannels()
            .channelBuilder()
            .withFriendlyName(typ + "_TestChannelF_" + value)
            .withUniqueName(typ + "_TestChannelU_" + value)
            .withType(type)
            .withAttributes(attrs)
            .build(new CallbackListener<Channel>() {
                @Override
                public void onSuccess(final Channel newChannel)
                {
                    logger.d("Successfully created a channel with options.");
                    channels.put(newChannel.getSid(), new ChannelModel(newChannel));
                    refreshChannelList();
                }

                @Override
                public void onError(ErrorInfo errorInfo)
                {
                    logger.e("Error creating a channel");
                }
            });
    }

    private void showCreateChannelDialog(final ChannelType type)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(ChannelActivity.this);
        String              title = "Enter " + type.toString() + " name";

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(getLayoutInflater().inflate(R.layout.dialog_add_channel, null))
            .setTitle(title)
            .setPositiveButton(
                "Create",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        String channelName =
                            ((EditText)createChannelDialog.findViewById(R.id.channel_name))
                                .getText()
                                .toString();
                        logger.d("Creating channel with friendly Name|" + channelName + "|");
                        channelsObject.createChannel(channelName, type, new CallbackListener<Channel>() {
                            @Override
                            public void onSuccess(final Channel newChannel)
                            {
                                logger.d("Successfully created a channel");
                                if (newChannel != null) {
                                    final String sid = newChannel.getSid();
                                    ChannelType  type = newChannel.getType();
                                    logger.d("Channel created with sid|" + sid + "| and type |"
                                             + type.toString()
                                             + "|");
                                    channels.put(newChannel.getSid(), new ChannelModel(newChannel));
                                    refreshChannelList();
                                }
                            }

                            @Override
                            public void onError(ErrorInfo errorInfo)
                            {
                                TwilioApplication.get().showError("Error creating channel",
                                                                     errorInfo);
                            }
                        });
                    }
                })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id)
                {
                    dialog.cancel();
                }
            });
        createChannelDialog = builder.create();
        createChannelDialog.show();
    }

    private void showSearchChannelDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(ChannelActivity.this);
        String              title = "Enter unique channel name";

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(getLayoutInflater().inflate(R.layout.dialog_search_channel, null))
            .setTitle(title)
            .setPositiveButton("Search", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id)
                {
                    String channelSid =
                        ((EditText)createChannelDialog.findViewById(R.id.channel_name))
                            .getText()
                            .toString();
                    logger.d("Searching for " + channelSid);
                    channelsObject.getChannel(channelSid, new CallbackListener<Channel>() {
                        @Override
                        public void onSuccess(final Channel channel) {
                            if (channel != null) {
                                TwilioApplication.get().showToast(channel.getSid() + ":" + channel.getFriendlyName());
                            } else {
                                TwilioApplication.get().showToast("Channel not found.");
                            }
                        }
                    });
                }
            });
        createChannelDialog = builder.create();
        createChannelDialog.show();
    }

    private void setupListView()
    {
        listView = (ListView)findViewById(R.id.channel_list);
        adapter = new EasyAdapter<ChannelModel>(
            this,
            ChannelViewHolder.class,
            adapterContents,
            new ChannelViewHolder.OnChannelClickListener() {
                @Override
                public void onChannelClicked(final ChannelModel channel)
                {
                    if (channel.getStatus() == Channel.ChannelStatus.JOINED) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run()
                            {
                                channel.getChannel(new CallbackListener<Channel>() {
                                                       @Override
                                                       public void onSuccess(Channel chan) {
                                                           Intent i = new Intent(ChannelActivity.this, MessageActivity.class);
                                                           i.putExtra(Constants.EXTRA_CHANNEL, (Parcelable)chan);
                                                           i.putExtra(Constants.EXTRA_CHANNEL_SID, chan.getSid());
                                                           startActivity(i);
                                                       }
                                                   });
                            }
                        }, 0);
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(ChannelActivity.this);
                    builder.setTitle("Select an option")
                        .setItems(CHANNEL_OPTIONS, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                if (which == JOIN) {
                                    dialog.cancel();
                                    channel.join(
                                        new ToastStatusListener("Successfully joined channel",
                                                                "Failed to join channel") {
                                        @Override
                                        public void onSuccess()
                                        {
                                            super.onSuccess();
                                            refreshChannelList();
                                        }
                                    });
                                }
                            }
                        });
                    builder.show();
                }
            });

        listView.setAdapter(adapter);
    }

    private void refreshChannelList()
    {
        adapterContents.clear();
        adapterContents.addAll(channels.values());
        Collections.sort(adapterContents, new CustomChannelComparator());
        adapter.notifyDataSetChanged();
    }

    private void getChannelsPage(Paginator<ChannelDescriptor> paginator) {
        for (ChannelDescriptor cd : paginator.getItems()) {
            logger.e("Adding channel descriptor for sid|"+cd.getSid()+"| friendlyName "+cd.getFriendlyName());
            channels.put(cd.getSid(), new ChannelModel(cd));
        }
        refreshChannelList();

        Log.e("HASNEXTPAGE", String.valueOf(paginator.getItems().size()));
        Log.e("HASNEXTPAGE", paginator.hasNextPage() ? "YES" : "NO");

        if (paginator.hasNextPage()) {
            paginator.requestNextPage(new CallbackListener<Paginator<ChannelDescriptor>>() {
                @Override
                public void onSuccess(Paginator<ChannelDescriptor> channelDescriptorPaginator) {
                    getChannelsPage(channelDescriptorPaginator);
                }
            });
        } else {
            // Get subscribed channels last - so their status will overwrite whatever we received
            // from public list. Ugly workaround for now.
            channelsObject = basicClient.getChatClient().getChannels();
            List<Channel> ch = channelsObject.getSubscribedChannels();
            for (Channel channel : ch) {
                channels.put(channel.getSid(), new ChannelModel(channel));
            }
            refreshChannelList();            
        }
    }

    // Initialize channels with channel list
    private void getChannels()
    {
        if (channels == null) return;
        if (basicClient == null || basicClient.getChatClient() == null) return;

        channelsObject = basicClient.getChatClient().getChannels();

        channels.clear();

        channelsObject.getPublicChannelsList(new CallbackListener<Paginator<ChannelDescriptor>>() {
            @Override
            public void onSuccess(Paginator<ChannelDescriptor> channelDescriptorPaginator) {
                getChannelsPage(channelDescriptorPaginator);
            }
        });

        channelsObject.getUserChannelsList(new CallbackListener<Paginator<ChannelDescriptor>>() {
            @Override
            public void onSuccess(Paginator<ChannelDescriptor> channelDescriptorPaginator) {
                getChannelsPage(channelDescriptorPaginator);
            }
        });
    }

    private void showIncomingInvite(final Channel channel)
    {
        handler.post(new Runnable() {
            @Override
            public void run()
            {
                if (incomingChannelInvite == null) {
                    incomingChannelInvite =
                        new AlertDialog.Builder(ChannelActivity.this)
                            .setTitle(R.string.channel_invite)
                            .setMessage(R.string.channel_invite_message)
                            .setPositiveButton(
                                R.string.join,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        channel.join(new ToastStatusListener(
                                            "Successfully joined channel",
                                            "Failed to join channel") {
                                            @Override
                                            public void onSuccess()
                                            {
                                                super.onSuccess();
                                                channels.put(channel.getSid(), new ChannelModel(channel));
                                                refreshChannelList();
                                            }
                                        });
                                        incomingChannelInvite = null;
                                    }
                                })
                            .setNegativeButton(
                                R.string.decline,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        channel.declineInvitation(new ToastStatusListener(
                                            "Successfully declined channel invite",
                                            "Failed to decline channel invite") {
                                            @Override
                                            public void onSuccess()
                                            {
                                                super.onSuccess();
                                            }
                                        });
                                        incomingChannelInvite = null;
                                    }
                                })
                            .create();
                }
                incomingChannelInvite.show();
            }
        });
    }

    private class CustomChannelComparator implements Comparator<ChannelModel>
    {
        @Override
        public int compare(ChannelModel lhs, ChannelModel rhs)
        {
            return lhs.getFriendlyName().compareTo(rhs.getFriendlyName());
        }
    }

    //=============================================================
    // ChatClientListener
    //=============================================================

    @Override
    public void onChannelJoined(final Channel channel)
    {
        logger.d("Received onChannelJoined callback for channel |" + channel.getFriendlyName() + "|");
        channels.put(channel.getSid(), new ChannelModel(channel));
        refreshChannelList();
    }

    @Override
    public void onChannelAdded(final Channel channel)
    {
        logger.d("Received onChannelAdd callback for channel |" + channel.getFriendlyName() + "|");
        channels.put(channel.getSid(), new ChannelModel(channel));
        refreshChannelList();
    }

    @Override
    public void onChannelUpdated(final Channel channel, final Channel.UpdateReason reason)
    {
        logger.d("Received onChannelChange callback for channel |" + channel.getFriendlyName()
                + "| with reason " + reason.toString());
        channels.put(channel.getSid(), new ChannelModel(channel));
        refreshChannelList();
    }

    @Override
    public void onChannelDeleted(final Channel channel)
    {
        logger.d("Received onChannelDelete callback for channel |" + channel.getFriendlyName()
                + "|");
        channels.remove(channel.getSid());
        refreshChannelList();
    }

    @Override
    public void onChannelInvited(final Channel channel)
    {
        channels.put(channel.getSid(), new ChannelModel(channel));
        refreshChannelList();
        showIncomingInvite(channel);
    }

    @Override
    public void onChannelSynchronizationChange(Channel channel)
    {
        logger.e("Received onChannelSynchronizationChange callback for channel |"
                 + channel.getFriendlyName()
                 + "| with new status " + channel.getStatus().toString());
        refreshChannelList();
    }

    @Override
    public void onClientSynchronization(ChatClient.SynchronizationStatus status)
    {
        logger.e("Received onClientSynchronization callback " + status.toString());
    }

    @Override
    public void onUserUpdated(User user, User.UpdateReason reason)
    {
        logger.e("Received onUserUpdated callback for "+reason.toString());
    }

    @Override
    public void onUserSubscribed(User user)
    {
        logger.e("Received onUserSubscribed callback");
    }

    @Override
    public void onUserUnsubscribed(User user)
    {
        logger.e("Received onUserUnsubscribed callback");
    }

    @Override
    public void onNotification(String channelId, String messageId)
    {
        logger.d("Received new push notification");
        TwilioApplication.get().showToast("Received new push notification");
    }

    @Override
    public void onNotificationSubscribed()
    {
        logger.d("Subscribed to push notifications");
        TwilioApplication.get().showToast("Subscribed to push notifications");
    }

    @Override
    public void onNotificationFailed(ErrorInfo errorInfo)
    {
        logger.d("Failed to subscribe to push notifications");
        TwilioApplication.get().showError("Failed to subscribe to push notifications", errorInfo);
    }

    @Override
    public void onError(ErrorInfo errorInfo)
    {
        TwilioApplication.get().showError("Received error", errorInfo);
    }

    @Override
    public void onConnectionStateChange(ChatClient.ConnectionState connectionState) {
        TwilioApplication.get().showToast("Transport state changed to "+connectionState.toString());
    }
}
