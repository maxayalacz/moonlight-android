package com.limelight.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal circular-ambience carousel adapter for Focus Mode on PcView.
 */
public class FocusPcAdapter extends RecyclerView.Adapter<FocusPcAdapter.ViewHolder> {

    public interface Callbacks {
        void onPcClicked(int position);
        void onPcOptionsClicked(int position);
    }

    private final List<PcView.ComputerObject> items = new ArrayList<>();
    private final Callbacks callbacks;
    private int activePage = 0;

    public FocusPcAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submitComputers(List<PcView.ComputerObject> computers) {
        items.clear();
        if (computers != null) {
            items.addAll(computers);
        }
        if (activePage >= items.size()) {
            activePage = Math.max(0, items.size() - 1);
        }
        notifyDataSetChanged();
    }

    public void setActivePage(int page) {
        if (page < 0 || page >= items.size()) {
            return;
        }
        if (this.activePage != page) {
            int old = this.activePage;
            this.activePage = page;
            notifyItemChanged(old);
            notifyItemChanged(page);
        }
    }

    public int getActivePage() {
        return activePage;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_focus_pc_circle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PcView.ComputerObject computer = items.get(position);
        ComputerDetails details = computer.details;
        Context context = holder.itemView.getContext();
        AppThemePalette palette = AppThemePalette.getCurrent(context);

        // Adapt circle diameter so compact landscape heights never clip labels
        int screenH = context.getResources().getConfiguration().screenHeightDp;
        boolean compactHeight = screenH > 0 && screenH < 430;
        int portalDp = compactHeight ? 168 : 228;
        int portalPx = Math.round(UiHelper.dpToPx(context, portalDp));
        ViewGroup.LayoutParams circleLp = holder.circleFrame.getLayoutParams();
        if (circleLp != null && circleLp.width != portalPx) {
            circleLp.width = portalPx;
            circleLp.height = portalPx;
            holder.circleFrame.setLayoutParams(circleLp);
        }

        holder.portal.bind(details, position == activePage);
        holder.nameText.setText(details.name);

        String addressText = "";
        if (details.activeAddress != null && details.activeAddress.address != null) {
            addressText = details.activeAddress.address;
        } else if (details.localAddress != null && details.localAddress.address != null) {
            addressText = details.localAddress.address;
        } else if (details.manualAddress != null && details.manualAddress.address != null) {
            addressText = details.manualAddress.address;
        }
        if (!addressText.isEmpty()) {
            holder.addressText.setVisibility(View.VISIBLE);
            holder.addressText.setText(addressText);
        } else {
            holder.addressText.setVisibility(View.GONE);
        }

        boolean isOnline = details.state == ComputerDetails.State.ONLINE;
        boolean isPaired = details.pairState == PairingManager.PairState.PAIRED;

        if (details.state == ComputerDetails.State.UNKNOWN) {
            holder.statusText.setText(R.string.pcview_menu_header_unknown);
            holder.statusText.setTextColor(0xA6FFFFFF);
            holder.hintText.setText(R.string.focus_mode_tap_options);
        } else if (!isOnline) {
            holder.statusText.setText(R.string.focus_mode_offline);
            holder.statusText.setTextColor(0xCCFF6E6E);
            holder.hintText.setText(R.string.focus_mode_tap_options);
        } else if (!isPaired) {
            holder.statusText.setText(R.string.focus_mode_unpaired);
            holder.statusText.setTextColor(0xE6FFD54F);
            holder.hintText.setText(R.string.focus_mode_tap_pair);
        } else if (details.runningGameId != 0) {
            holder.statusText.setText(R.string.focus_mode_in_game);
            holder.statusText.setTextColor(AppThemePalette.withAlpha(palette.highlight, 0.92f));
            holder.hintText.setText(R.string.focus_mode_tap_open);
        } else {
            holder.statusText.setText(R.string.focus_mode_ready);
            holder.statusText.setTextColor(AppThemePalette.withAlpha(palette.highlight, 0.92f));
            holder.hintText.setText(R.string.focus_mode_tap_open);
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && callbacks != null) {
                callbacks.onPcClicked(pos);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && callbacks != null) {
                callbacks.onPcOptionsClicked(pos);
                return true;
            }
            return false;
        });
        holder.moreButton.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && callbacks != null) {
                callbacks.onPcOptionsClicked(pos);
            }
        });
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> holder.portal.setPressedOrFocused(hasFocus));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ViewGroup circleFrame;
        final FocusCirclePortalView portal;
        final ImageButton moreButton;
        final TextView nameText;
        final TextView addressText;
        final TextView statusText;
        final TextView hintText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            circleFrame = itemView.findViewById(R.id.focusCircleFrame);
            portal = itemView.findViewById(R.id.focusCirclePortal);
            moreButton = itemView.findViewById(R.id.focusMoreButton);
            nameText = itemView.findViewById(R.id.focusPcName);
            addressText = itemView.findViewById(R.id.focusPcAddress);
            statusText = itemView.findViewById(R.id.focusPcStatus);
            hintText = itemView.findViewById(R.id.focusPcHint);
        }
    }
}
