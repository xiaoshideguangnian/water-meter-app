package com.example.waterapp.ui;

import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import com.example.waterapp.R;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {
    private List<List<String>> data;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;

    public interface OnItemClickListener {
        void onItemClick(List<String> row, int position);
    }
    public interface OnItemLongClickListener {
        boolean onItemLongClick(List<String> row, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) { this.onItemClickListener = listener; }
    public void setOnItemLongClickListener(OnItemLongClickListener listener) { this.onItemLongClickListener = listener; }

    public DataAdapter(List<List<String>> data) {
        this.data = data;
    }

    public void setData(List<List<String>> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_data_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        List<String> row = data.get(position);
        holder.bind(row, position);
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        ViewHolder(View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_row_content);
            itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) onItemClickListener.onItemClick(data.get(pos), pos);
                }
            });
            itemView.setOnLongClickListener(v -> {
                if (onItemLongClickListener != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) return onItemLongClickListener.onItemLongClick(data.get(pos), pos);
                }
                return false;
            });
        }

        void bind(List<String> row, int pos) {
            StringBuilder sb = new StringBuilder();
            for (String cell : row) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(cell);
            }
            tvContent.setText(sb.toString());
        }
    }
}
