package com.example.tuktukbuddy.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tuktukbuddy.R;
import com.example.tuktukbuddy.model.RideRequest;

import java.util.ArrayList;
import java.util.List;

public class ManageRequestsAdapter extends RecyclerView.Adapter<ManageRequestsAdapter.ManageRequestViewHolder> {

    public interface OnManageRequestListener {
        void onAccept(RideRequest request);
        void onReject(RideRequest request);
    }

    private final List<RideRequest> requests = new ArrayList<>();
    private OnManageRequestListener listener;

    public void setOnManageRequestListener(OnManageRequestListener listener) {
        this.listener = listener;
    }

    public void setRequests(List<RideRequest> newRequests) {
        requests.clear();
        if (newRequests != null) {
            requests.addAll(newRequests);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ManageRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manage_request, parent, false);
        return new ManageRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ManageRequestViewHolder holder, int position) {
        RideRequest request = requests.get(position);
        holder.bind(request, listener);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ManageRequestViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvManageRequestRoute;
        private final TextView tvManageRequestStatus;
        private final TextView tvManageRequestDateTime;
        private final TextView tvManageRequesterName;
        private final LinearLayout layoutActionButtons;
        private final Button btnAccept;
        private final Button btnReject;

        public ManageRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            tvManageRequestRoute = itemView.findViewById(R.id.tvManageRequestRoute);
            tvManageRequestStatus = itemView.findViewById(R.id.tvManageRequestStatus);
            tvManageRequestDateTime = itemView.findViewById(R.id.tvManageRequestDateTime);
            tvManageRequesterName = itemView.findViewById(R.id.tvManageRequesterName);
            layoutActionButtons = itemView.findViewById(R.id.layoutActionButtons);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
        }

        public void bind(RideRequest request, OnManageRequestListener listener) {
            if (request == null) return;

            Context context = itemView.getContext();
            tvManageRequestRoute.setText(request.getTripRoute());
            tvManageRequestDateTime.setText(String.format("📅 %s", request.getTripDateTime()));
            tvManageRequesterName.setText(context.getString(R.string.requester_label, request.getRequesterName()));

            String status = request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING";

            if ("PENDING".equalsIgnoreCase(status)) {
                layoutActionButtons.setVisibility(View.VISIBLE);
                tvManageRequestStatus.setVisibility(View.GONE);

                btnAccept.setOnClickListener(v -> {
                    if (listener != null) listener.onAccept(request);
                });

                btnReject.setOnClickListener(v -> {
                    if (listener != null) listener.onReject(request);
                });
            } else {
                layoutActionButtons.setVisibility(View.GONE);
                tvManageRequestStatus.setVisibility(View.VISIBLE);
                tvManageRequestStatus.setText(status);

                if ("ACCEPTED".equalsIgnoreCase(status)) {
                    tvManageRequestStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_accepted_container));
                    tvManageRequestStatus.setTextColor(ContextCompat.getColor(context, R.color.status_accepted));
                } else {
                    tvManageRequestStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_rejected_container));
                    tvManageRequestStatus.setTextColor(ContextCompat.getColor(context, R.color.status_rejected));
                }
            }
        }
    }
}