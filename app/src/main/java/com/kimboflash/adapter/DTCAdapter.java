package com.kimboflash.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.kimboflash.R;
import com.kimboflash.model.DTC;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

public class DTCAdapter extends RecyclerView.Adapter<DTCAdapter.ViewHolder> {
    private final List<DTC> items = new ArrayList<>();

    public DTCAdapter(List<DTC> initial) {
        setItems(initial);
    }

    public void setItems(List<DTC> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dtc, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder h, int pos) {
        DTC dtc = items.get(pos);
        h.tvCode.setText(dtc.getCode());
        h.tvDescription.setText(dtc.getDescription());
        h.tvTimestamp.setText(DateFormat.getDateTimeInstance().format(dtc.getTimestamp()));
        h.tvStatus.setText(dtc.getStatus());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCode, tvDescription, tvTimestamp, tvStatus;
        public ViewHolder(View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tvCode);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
