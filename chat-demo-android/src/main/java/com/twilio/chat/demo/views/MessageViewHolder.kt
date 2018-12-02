package com.twilio.chat.demo.views

import android.content.Context
import com.twilio.chat.CallbackListener
import com.twilio.chat.Member
import com.twilio.chat.User
import com.twilio.chat.demo.R
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import kotterknife.bindView
import com.twilio.chat.demo.TwilioApplication
import com.twilio.chat.demo.activities.MessageActivity
import eu.inloop.simplerecycleradapter.SettableViewHolder

class MessageViewHolder : SettableViewHolder<MessageActivity.MessageItem> {
    val imageView: ImageView by bindView(R.id.avatar)
    val reachabilityView: ImageView by bindView(R.id.reachability)
    val body: TextView by bindView(R.id.body)
    val author: TextView by bindView(R.id.author)
    val date: TextView by bindView(R.id.date)
    val identities: RelativeLayout by bindView(R.id.consumptionHorizonIdentities)
    val lines: LinearLayout by bindView(R.id.consumptionHorizonLines)

    constructor(context: Context, parent: ViewGroup)
            : super(context, R.layout.message_item_layout, parent)
    {}

    override fun setData(message: MessageActivity.MessageItem) {
        val msg = message.message

        author.text = msg.author
        body.text = msg.messageBody
        date.text = msg.dateCreated

        identities.removeAllViews()
        lines.removeAllViews()

        for (member in message.members.membersList) {
            if (msg.author.contentEquals(member.identity)) {
                fillUserAvatar(imageView, member)
                fillUserReachability(reachabilityView, member)
            }

            if (member.lastConsumedMessageIndex != null && member.lastConsumedMessageIndex == message.message.messageIndex) {
                drawConsumptionHorizon(member)
            }
        }
    }

    fun toggleDateVisibility() {
        date.visibility = if (date.visibility == View.GONE) View.VISIBLE else View.GONE
    }

    private fun drawConsumptionHorizon(member: Member) {
        val ident = member.identity
        val color = getMemberRgb(ident)

        val identity = TextView(itemView.context)
        identity.text = ident
        identity.textSize = 8f
        identity.setTextColor(color)

        // Layout
        val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        val cc = identities.childCount
        if (cc > 0) {
            params.addRule(RelativeLayout.RIGHT_OF, identities.getChildAt(cc - 1).id)
        }
        identity.layoutParams = params

        val line = View(itemView.context)
        line.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 5)
        line.setBackgroundColor(color)

        identities.addView(identity)
        lines.addView(line)
    }

    private fun fillUserAvatar(avatarView: ImageView, member: Member) {
        TwilioApplication.instance.basicClient.chatClient?.users?.getAndSubscribeUser(member.identity, object : CallbackListener<User>() {
            override fun onSuccess(user: User) {
                val attributes = user.attributes
                val avatar = attributes.opt("avatar") as String?
                if (avatar != null) {
                    val data = Base64.decode(avatar, Base64.NO_WRAP)
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    avatarView.setImageBitmap(bitmap)
                } else {
                    avatarView.setImageResource(R.drawable.avatar2)
                }
            }
        })
    }

    private fun fillUserReachability(reachabilityView: ImageView, member: Member) {
        if (!TwilioApplication.instance.basicClient.chatClient?.isReachabilityEnabled!!) {
            reachabilityView.setImageResource(R.drawable.reachability_disabled)
        } else {
            member.getAndSubscribeUser(object : CallbackListener<User>() {
                override fun onSuccess(user: User) {
                    if (user.isOnline) {
                        reachabilityView.setImageResource(R.drawable.reachability_online)
                    } else if (user.isNotifiable) {
                        reachabilityView.setImageResource(R.drawable.reachability_notifiable)
                    } else {
                        reachabilityView.setImageResource(R.drawable.reachability_offline)
                    }
                }
            })
        }
    }

    fun getMemberRgb(identity: String): Int {
        return HORIZON_COLORS[Math.abs(identity.hashCode()) % HORIZON_COLORS.size]
    }

    companion object {
        private val HORIZON_COLORS = intArrayOf(Color.GRAY, Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA)
    }
}
