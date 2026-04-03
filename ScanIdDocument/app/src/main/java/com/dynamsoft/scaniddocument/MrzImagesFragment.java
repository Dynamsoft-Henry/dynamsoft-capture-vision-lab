package com.dynamsoft.scaniddocument;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.core.basic_structures.ImageData;

public class MrzImagesFragment extends Fragment {
	private final ImageData imageData1;
	private final ImageData imageData2;

	public MrzImagesFragment(@Nullable ImageData imageData1, @Nullable ImageData imageData2) {
		super();
		this.imageData1 = imageData1;
		this.imageData2 = imageData2;
	}

	@NonNull
	public static MrzImagesFragment newInstance(@Nullable ImageData imageData1, @Nullable ImageData imageData2) {
		return new MrzImagesFragment(imageData1, imageData2);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull android.view.LayoutInflater inflater,
							 @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		LinearLayout root = new LinearLayout(requireContext());
		root.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		root.setOrientation(LinearLayout.HORIZONTAL);
		root.setGravity(Gravity.CENTER_VERTICAL);
		root.setBaselineAligned(false);
		root.setClipToPadding(false);
		root.setClipChildren(false);
		return root;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		LinearLayout root = (LinearLayout) view;
		ImageData[] imageDatas = new ImageData[]{imageData1, imageData2};
		for (int index = 0; index < imageDatas.length; index++) {
			ImageData imageData = imageDatas[index];
			if (imageData1 != null && imageData2 != null && index == 1) {
				root.addView(new View(requireContext()),
						new LinearLayout.LayoutParams((int) (16 * getResources().getDisplayMetrics().density),
						ViewGroup.LayoutParams.MATCH_PARENT));
			}
			if (imageData != null) {
				try {
					Bitmap bitmap = imageData.toBitmap();
					ImageView imageView = new ImageView(requireContext());
					LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
					imageView.setLayoutParams(params);
					imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
					imageView.setAdjustViewBounds(true);
					imageView.setImageBitmap(bitmap);
					root.addView(imageView);
				} catch (CoreException ignored) {
				}
			}
		}
	}
}