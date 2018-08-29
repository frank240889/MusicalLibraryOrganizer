package mx.dev.franco.automusictagfixer.datasource.cover_loader;

import android.os.AsyncTask;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Extracts a loads cover from audiofiles using
 * another thread.
 */
public class AsyncLoaderCover extends AsyncTask<String, Void, byte[]> {
    private CoverLoaderListener mListener;
    public AsyncLoaderCover() {}

    public void setListener(CoverLoaderListener listener){
        mListener = listener;
    }

    @Override
    protected void onPreExecute(){
        if(mListener != null)
            mListener.onLoadingStart();
    }

    @Override
    protected byte[] doInBackground(String... params) {
        String path = params[0];
        File file = new File(path);
        if(!file.exists()) {
            return null;
        }

        try {
            AudioFile audioTaggerFile = AudioFileIO.read(new File(path));
            Tag tag = null;
            byte[] cover = null;
            if (audioTaggerFile.getTag() == null) {
                return null;
            }

            tag = audioTaggerFile.getTag();

            if (tag.getFirstArtwork() == null) {
                return null;
            }

            if(tag.getFirstArtwork().getBinaryData() == null){
                return null;
            }

            cover = tag.getFirstArtwork().getBinaryData();
            return cover;

        }
        catch(IOException | CannotReadException | ReadOnlyFileException | InvalidAudioFrameException | TagException e){
            e.printStackTrace();
            return null;
        }
    }
    @Override
    protected void onPostExecute(byte[] cover){
        if(mListener != null)
            mListener.onLoadingFinished(cover);
        mListener = null;
    }

    @Override
    public void onCancelled(byte[] cover){
        if(mListener != null)
            mListener.onLoadingCancelled();
        mListener = null;
    }
}