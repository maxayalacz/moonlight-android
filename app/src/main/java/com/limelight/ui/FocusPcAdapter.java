package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
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
import java.util.Objects;

/**
 * Horizontal circular-ambience carousel adapter for Focus Mode on PcView.
 */
public class FocusPcAdapter extends RecyclerView.Adapter<FocusPcAdapter.ViewHolder> {

    private static final Object PAYLOAD_ACTIVE_PAGE = new Object();
    private static final PathInterpolator PRESS_INTERPOLATOR = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    public interface Callbacks {
        void onPcClicked(int position);
        void onPcOptionsClicked(int position);
    }

    private static final class Snapshot {
        final String uuid;
        final String name;
        final String address;
        final ComputerDetails.State state;
        final PairingManager.PairState pairState;
        final int runningGameId;

        Snapshot(ComputerDetails d) {
            this.uuid = d.uuid;
            this.name = d.name;
            this.address = extractAddress(d);
            this.state = d.state;
            this.pairState = d.pairState;
            this.runningGameId = d.runningGameId;
        }

        boolean matches(Snapshot other) {
            return Objects.equals(uuid, other.uuid)
                    && Objects.equals(name, other.name)
                    && Objects.equals(address, other.address)
                    && state == other.state
                    && pairState == other.pairState
                    && runningGameId == other.runningGameId;
        }
    }

    private final List<PcView.ComputerObject> items = new ArrayList<>();
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final Callbacks callbacks;
    private int activePage = 0;
    private String lastPaletteKey = "";

    public FocusPcAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    private static String extractAddress(ComputerDetails details) {
        if (details.activeAddress != null && details.activeAddress.address != null) {
            return details.activeAddress.address;
        } else if (details.localAddress != null && details.localAddress.address != null) {
            return details.localAddress.address;
        } else if (details.manualAddress != null && details.manualAddress.address != null) {
            return details.manualAddress.address;
        }
        return "";
    }

    public void submitComputers(List<PcView.ComputerObject> computers, String paletteKey) {
        String nextPalette = paletteKey != null ? paletteKey : "";
        boolean paletteChanged = !nextPalette.equals(lastPaletteKey);
        lastPaletteKey = nextPalette;

        List<PcView.ComputerObject> incoming = computers != null ? computers : new ArrayList<>();
        boolean structuralChange = paletteChanged || incoming.size() != snapshots.size();
        if (!structuralChange) {
            for (int i = 0; i < incoming.size(); i++) {
                if (!Objects.equals(snapshots.get(i).uuid, incoming.get(i).details.uuid)) {
                    structuralChange = true;
                    break;
                }
            }
        }

        if (structuralChange) {
            items.clear();
            snapshots.clear();
            for (PcView.ComputerObject obj : incoming) {
                items.add(obj);
                snapshots.add(new Snapshot(obj.details));
            }
            if (activePage >= items.size()) {
                activePage = Math.max(0, items.size() - 1);
            }
            notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < incoming.size(); i++) {
            PcView.ComputerObject obj = incoming.get(i);
            Snapshot next = new Snapshot(obj.details);
            items.set(i, obj);
            if (!snapshots.get(i).matches(next)) {
                snapshots.set(i, next);
                notifyItemChanged(i);
            }
        }
    }

    public void setActivePage(int page) {
        if (page < 0 || page >= items.size()) {
            return;
        }
        if (this.activePage != page) {
            int old = this.activePage;
            this.activePage = page;
            notifyItemChanged(old, PAYLOAD_ACTIVE_PAGE);
            notifyItemChanged(page, PAYLOAD_ACTIVE_PAGE);
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
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_ACTIVE_PAGE)) {
            PcView.ComputerObject computer = items.get(position);
            holder.portal.bind(computer.details, position == activePage);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PcView.ComputerObject computer = items.get(position);
        ComputerDetails details = computer.details;
        Context context = holder.itemView.getContext();
        AppThemePalette palette = AppThemePalette.getCurrent(context);

        holder.circleFrame.animate().cancel();
        holder.circleFrame.setScaleX(1f);
        holder.circleFrame.setScaleY(1f);

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

        String addressText = extractAddress(details);
        if (!addressText.isEmpty()) {
            holder.addressText.setVisibility(View.VISIBLE);
            holder.addressText.setText(addressText);
        } else {
            holder.addressText.setVisibility(View.GONE);
        }

        boolean isOnline = details.state == ComputerDetails.State.ONLINE;
        boolean isPaired = details.pairState == PairingManager.PairState.PAIRED;

        if (details.state == ComputerDetails.State.UNKNOWN) {
            holder.statusText.setText(R.string.focus_mode_status_searching);
            holder.statusText.setTextColor(0xA6FFFFFF);
            holder.hintText.setText(R.string.focus_mode_tap_options);
        } else if (!isOnline) {
            holder.statusText.setText(R.string.focus_mode_status_offline);
            holder.statusText.setTextColor(0xCCFF6E6E);
            holder.hintText.setText(R.string.focus_mode_tap_options);
        } else if (!isPaired) {
            holder.statusText.setText(R.string.focus_mode_status_unpaired);
            holder.statusText.setTextColor(0xE6FFD54F);
            holder.hintText.setText(R.string.focus_mode_tap_pair);
        } else if (details.runningGameId != 0) {
            holder.statusText.setText(R.string.focus_mode_status_ingame);
            holder.statusText.setTextColor(AppThemePalette.withAlpha(palette.highlight, 0.92f));
            holder.hintText.setText(R.string.focus_mode_tap_open);
        } else {
            holder.statusText.setText(R.string.focus_mode_status_online);
            holder.statusText.setTextColor(AppThemePalette.withAlpha(palette.highlight, 0.92f));
            holder.hintText.setText(R.string.focus_mode_tap_open);
        }

        holder.itemView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                holder.portal.setPressedOrFocused(true);
                holder.circleFrame.animate()
                        .scaleX(0.955f)
                        .scaleY(0.955f)
                        .setDuration(110L)
                        .setInterpolator(PRESS_INTERPOLATOR)
                        .start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                holder.portal.setPressedOrFocused(v.isFocused());
                holder.circleFrame.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(PRESS_INTERPOLATOR)
                        .start();
            }
            return false;
        });

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
