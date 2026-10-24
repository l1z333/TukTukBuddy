package com.example.tuktukbuddy.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tuktukbuddy.R;
import com.example.tuktukbuddy.model.Trip;

import java.util.ArrayList;
import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    public interface OnTripRequestListener {
        void onRequestJoin(Trip trip);
    }

    private final List<Trip> trips = new ArrayList<>();
    private OnTripRequestListener requestListener;

    public void setOnTripRequestListener(OnTripRequestListener listener) {
        this.requestListener = listener;
    }

    public void setTrips(List<Trip> newTrips) {
        trips.clear();
        if (newTrips != null) {
            trips.addAll(newTrips);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);
        holder.bind(trip, requestListener);
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTripRoute;
        private final TextView tvTripStatus;
        private final TextView tvTripDateTime;
        private final TextView tvTripSeats;
        private final TextView tvTripCreator;
        private final TextView tvTripNotes;
        private final Button btnRequestJoin;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTripRoute = itemView.findViewById(R.id.tvTripRoute);
            tvTripStatus = itemView.findViewById(R.id.tvTripStatus);
            tvTripDateTime = itemView.findViewById(R.id.tvTripDateTime);
            tvTripSeats = itemView.findViewById(R.id.tvTripSeats);
            tvTripCreator = itemView.findViewById(R.id.tvTripCreator);
            tvTripNotes = itemView.findViewById(R.id.tvTripNotes);
            btnRequestJoin = itemView.findViewById(R.id.btnRequestJoin);
        }

        public void bind(Trip trip, OnTripRequestListener listener) {
            if (trip == null) return;

            String route = itemView.getContext().getString(R.string.trip_route_format, trip.getPickup(), trip.getDestination());
            tvTripRoute.setText(route);

            String dateTime = itemView.getContext().getString(R.string.trip_date_time_format, trip.getDate(), trip.getTime());
            tvTripDateTime.setText(dateTime);

            String seats = itemView.getContext().getString(R.string.trip_seats_format, trip.getAvailableSeats());
            tvTripSeats.setText(seats);

            String creator = itemView.getContext().getString(R.string.trip_creator_format, trip.getCreatorName());
            tvTripCreator.setText(creator);

            if (trip.getStatus() != null && !trip.getStatus().isEmpty()) {
                tvTripStatus.setText(trip.getStatus());
            } else {
                tvTripStatus.setText(R.string.trip_status_open);
            }

            if (trip.getNotes() != null && !trip.getNotes().trim().isEmpty()) {
                tvTripNotes.setVisibility(View.VISIBLE);
                tvTripNotes.setText(String.format("Notes: %s", trip.getNotes().trim()));
            } else {
                tvTripNotes.setVisibility(View.GONE);
            }

            btnRequestJoin.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRequestJoin(trip);
                }
            });
        }
    }
}