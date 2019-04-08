package org.tensorflow.demo;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class TutorialFragmentAdapter extends FragmentPagerAdapter {

  private Context mContext;

  TutorialFragmentAdapter(Context context, FragmentManager fragmentManager){
    super(fragmentManager);
    mContext = context;
  }

  @Override
  public Fragment getItem(int position) {
    if (position == 0) { // TODO case statement here
      return new MapTutorialFragment();

    } else {
      return new RecordingTutorialFragment();
    }
  }


  @Override
  public int getCount() {
    return 2;
  }
}
