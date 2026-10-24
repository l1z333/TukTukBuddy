package com.example.tuktukbuddy.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tuktukbuddy.R;
import com.example.tuktukbuddy.model.RideRequest;

import java.util.ArrayList;
import java.util.List;

public class MyRequestsAdapter extends RecyclerView.Adapter<MyRequestsAdapter.MyRequestViewHolder> {

    private final List<RideRequest> requests = new ArrayList<>();

    public void setRequests(List<RideRequest> newRequests) {
        requests.clear();
        if (newRequests != null) {
            requests.addAll(newRequests);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_request, parent, false);
        return new MyRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyRequestViewHolder holder, int position) {
        RideRequest request = requests.get(position);
        holder.bind(request);
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class MyRequestViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvMyRequestRoute;
        private final TextView tvMyRequestStatus;
        private final TextView tvMyRequestDateTime;

        public MyRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMyRequestRoute = itemView.findViewById(R.id.tvMyRequestRoute);
            tvMyRequestStatus = itemView.findViewById(R.id.tvMyRequestStatus);
            tvMyRequestDateTime = itemView.findViewById(R.id.tvMyRequestDateTime);
        }

        public void bind(RideRequest request) {
            if (request == null) return;

            Context context = itemView.getContext();
            tvMyRequestRoute.setText(request.getTripRoute());
            tvMyRequestDateTime.setText(String.format("📅 %s", request.getTripDateTime()));

            String status = request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING";
            tvMyRequestStatus.setText(status);

            if ("ACCEPTED".equalsIgnoreCase(status)) {
                tvMyRequestStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_accepted_container));
                tvMyRequestStatus.setTextColor(ContextCompat.getColor(context, R.color.status_accepted));
            } else if ("REJECTED".equalsIgnoreCase(status)) {
                tvMyRequestStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_rejected_container));
                tvMyRequestStatus.setTextColor(ContextCompat.getColor(context, R.color.status_rejected));
            } else {
                tvMyRequestStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_pending_container));
                tvMyRequestStatus.setTextColor(ContextCompat.getColor(context, R.color.status_pending));
            }
        }
    }
}