package com.example.waterapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private List<WaterData> data = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<WaterData> newData) {
        this.data = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_data, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WaterData item = data.get(position);
        // 显示第一列数据（通常为编号）
        Map<String, String> row = item.getRowData();
        String display = "";
        if (!row.isEmpty()) {
            // 取第一个值
            display = row.values().iterator().next();
        }
        holder.tvRowIndex.setText(String.valueOf(position + 1));
        holder.tvRowData.setText(display);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(v, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRowIndex, tvRowData;

        ViewHolder(View itemView) {
            super(itemView);
            tvRowIndex = itemView.findViewById(R.id.tv_row_index);
            tvRowData = itemView.findViewById(R.id.tv_row_data);
        }
    }
}
