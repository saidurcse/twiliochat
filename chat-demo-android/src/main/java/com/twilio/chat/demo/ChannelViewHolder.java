package com.twilio.chat.demo;

import com.twilio.chat.demo.ChannelModel;
import com.twilio.chat.Channel.ChannelStatus;
import com.twilio.chat.Channel.ChannelType;
import com.twilio.chat.CallbackListener;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import uk.co.ribot.easyadapter.ItemViewHolder;
import uk.co.ribot.easyadapter.PositionInfo;
import uk.co.ribot.easyadapter.annotations.LayoutId;
import uk.co.ribot.easyadapter.annotations.ViewId;

@LayoutId(R.layout.channel_item_layout)
public class ChannelViewHolder extends ItemViewHolder<ChannelModel>
{
    @ViewId(R.id.channel_friendly_name)
    TextView friendlyName;

    @ViewId(R.id.channel_sid)
    TextView channelSid;

    @ViewId(R.id.channel_updated_date)
    TextView updatedDate;

    @ViewId(R.id.channel_created_date)
    TextView createdDate;

    @ViewId(R.id.channel_users_count)
    TextView usersCount;

    @ViewId(R.id.channel_total_messages_count)
    TextView totalMessagesCount;

    @ViewId(R.id.channel_unconsumed_messages_count)
    TextView unconsumedMessagesCount;

    View view;

    public ChannelViewHolder(View view)
    {
        super(view);
        this.view = view;
    }

    @Override
    public void onSetListeners()
    {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                OnChannelClickListener listener = getListener(OnChannelClickListener.class);
                if (listener != null) {
                    listener.onChannelClicked(getItem());
                }
            }
        });
    }

    @Override
    public void onSetValues(ChannelModel channel, PositionInfo arg1)
    {
        friendlyName.setText(channel.getFriendlyName());
        channelSid.setText(channel.getSid());

        String updated = channel.getDateUpdatedAsDate() != null ?
                             channel.getDateUpdatedAsDate().toString() :
                             "<no updated date>";
        updatedDate.setText(updated);

        String created = channel.getDateCreatedAsDate() != null ?
                             channel.getDateCreatedAsDate().toString() :
                             "<no created date>";
        createdDate.setText(created);

        channel.getUnconsumedMessagesCount(new CallbackListener<Long>() {
            @Override
            public void onSuccess(Long value) {
                Log.d("ChannelViewHolder", "getUnconsumedMessagesCount callback");
                unconsumedMessagesCount.setText("Unread "+value.toString());
            }
        });

        channel.getMessagesCount(new CallbackListener<Long>() {
            @Override
            public void onSuccess(Long value) {
                Log.d("ChannelViewHolder", "getMessagesCount callback");
                totalMessagesCount.setText("Messages "+value.toString());
            }
        });

        channel.getMembersCount(new CallbackListener<Long>() {
            @Override
            public void onSuccess(Long value) {
                Log.d("ChannelViewHolder", "getMembersCount callback");
                usersCount.setText("Members "+value.toString());
            }
        });

        if (channel.getStatus() == ChannelStatus.JOINED) {
            view.setBackgroundColor(channel.getType() == ChannelType.PRIVATE ?
                Color.BLUE : Color.WHITE);    
        } else {
            view.setBackgroundColor(channel.getStatus() == ChannelStatus.INVITED ?
                Color.YELLOW : Color.GRAY);
        }
    }

    public interface OnChannelClickListener {
        void onChannelClicked(ChannelModel channel);
    }
}
