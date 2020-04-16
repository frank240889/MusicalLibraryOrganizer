package mx.dev.franco.automusictagfixer.ui.main;

import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import mx.dev.franco.automusictagfixer.R;
import mx.dev.franco.automusictagfixer.ui.AudioHolder;

/**
 * @author Franco Castillo
 */
public class AudioItemHolder extends AudioHolder implements View.OnClickListener {
    public interface ClickListener{
        void onCoverClick(int position, View view);
        void onCheckboxClick(int position);
        void onCheckMarkClick(int position);
        void onItemClick(int position, View view);
    }


    public CheckBox checkBox;
    public TextView trackName;
    public TextView artistName;
    public TextView albumName;
    public ImageButton stateMark;
    public ProgressBar progressBar;
    public ClickListener mListener;

    public AudioItemHolder(View itemView, ClickListener clickListener) {
        super(itemView);
        checkBox = itemView.findViewById(R.id.check_box);
        checkBox.setChecked(false);
        trackName = itemView.findViewById(R.id.track_name);
        artistName = itemView.findViewById(R.id.artist_name);
        albumName = itemView.findViewById(R.id.album_name);
        progressBar =  itemView.findViewById(R.id.progress_processing_file);
        cover = itemView.findViewById(R.id.cover_art);
        stateMark = itemView.findViewById(R.id.status_mark);
        mListener = clickListener;
        checkBox.setOnClickListener(this);
        cover.setOnClickListener(this);
        itemView.setOnClickListener(this);
    }

    /**
     * This method of listener is implemented in
     * the host that creates the adapter and data source
     * @param v The view clicked.
     */
    @Override
    public void onClick(View v) {
        if (mListener != null) {
            switch (v.getId()){
                case R.id.check_box:
                        mListener.onCheckboxClick(getAdapterPosition());
                    break;
                case R.id.cover_art:
                        mListener.onCoverClick(getAdapterPosition(), v);
                    break;
                /*case R.id.status_mark:
                        mListener.onCheckMarkClick(getAdapterPosition());
                    break;*/
                default:
                        mListener.onItemClick(getAdapterPosition(), cover);
                    break;
            }
        }
    }
}
