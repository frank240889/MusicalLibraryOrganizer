package mx.dev.franco.automusictagfixer.UI.track_detail;

import android.view.View;
import android.widget.TextView;

import mx.dev.franco.automusictagfixer.R;
import mx.dev.franco.automusictagfixer.UI.AudioHolder;

public class ResultItemHolder extends AudioHolder {
    public TextView imageDimensions;
    public TextView title;
    public TextView artist;
    public TextView album;
    public TextView genre;
    public TextView trackNumber;
    public TextView trackYear;
    public ResultItemHolder(View itemView) {
        super(itemView);
        cover = itemView.findViewById(R.id.trackid_cover);
        imageDimensions = itemView.findViewById(R.id.trackid_cover_dimensions);
        title = itemView.findViewById(R.id.track_id_title);
        artist = itemView.findViewById(R.id.track_id_artist);
        genre = itemView.findViewById(R.id.trackid_genre);
        trackNumber = itemView.findViewById(R.id.track_id_number);
        trackYear = itemView.findViewById(R.id.track_id_year);
    }
}
