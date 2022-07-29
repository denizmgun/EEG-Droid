package de.uos.ikw.eegdroid.utilities;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import de.uos.ikw.eegdroid.R;


public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.PlanetViewHolder> {

    private final boolean[] isSelectedPosition;
    ArrayList<File> arrayListFiles;
    private int selectedPos = RecyclerView.NO_POSITION;

    public SessionAdapter(ArrayList<File> arrayListFiles, Context applicationContext) {
        this.arrayListFiles = arrayListFiles;
        int nItems = arrayListFiles.size();
        isSelectedPosition = new boolean[nItems];
    }

    @NonNull
    @Override
    public SessionAdapter.PlanetViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.session_row, parent, false);
        return new PlanetViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionAdapter.PlanetViewHolder holder, final int position) {

        BasicFileAttributes attrs;
        Path path = arrayListFiles.get(position).toPath();

        try {
            attrs = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException ex) {
            attrs = null;
        }

        holder.name.setText(arrayListFiles.get(position).getName());
        assert attrs != null;
        float kbs = attrs.size() / 1000.0f;
        holder.kbs.setText(Float.toString(kbs));

        ZonedDateTime creationTime = attrs.creationTime().toInstant().atZone(ZoneId.systemDefault());
        holder.date.setText(creationTime.toLocalDate().toString());
        holder.hour.setText(creationTime.toLocalTime().toString());

        //This handles the selection of an item in the list
        //holder.itemView.setSelected(selectedPos == position);
        boolean isSelected = isSelectedPosition[position];
        holder.itemView.setSelected(isSelected);

        holder.linearLayout.setOnClickListener(view -> {
            Log.d("Adapter.getSelectedPos", Integer.toString(position));
            //notifyItemChanged(selectedPos);
            //selectedPos = position;
            //notifyItemChanged(selectedPos);
            isSelectedPosition[position] = !isSelected;
            notifyItemChanged(position);
        });

    }

    @Override
    public int getItemCount() {
        return arrayListFiles.size();
    }

    public int getSelectedPos() {
        return selectedPos;
    }

    public boolean[] getSelectedPositions() {
        return isSelectedPosition;
    }

    public void resetSelectedPos() {
        selectedPos = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public static class PlanetViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout linearLayout;
        protected TextView name;
        protected TextView kbs;
        protected TextView date;
        protected TextView hour;

        public PlanetViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_id);
            kbs = itemView.findViewById(R.id.session_kbs);
            date = itemView.findViewById(R.id.session_date);
            hour = itemView.findViewById(R.id.session_hour);
            linearLayout = itemView.findViewById(R.id.linear_layout_session_row);
        }
    }


}
