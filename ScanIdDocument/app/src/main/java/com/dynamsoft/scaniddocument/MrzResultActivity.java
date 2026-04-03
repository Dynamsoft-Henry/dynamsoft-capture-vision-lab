package com.dynamsoft.scaniddocument;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.core.basic_structures.ImageData;

public class MrzResultActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mrz_result);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		MrzScanResult scanResult = PendingMrzResultStore.get();
		if (scanResult == null) {
			findViewById(R.id.result_view).setVisibility(View.GONE);
			TextView noResultView = findViewById(R.id.no_result_view);
			noResultView.setVisibility(View.VISIBLE);
			noResultView.setText("No result available.");
			return;
		}
		showMrzScanResult(scanResult);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (isFinishing()) {
			PendingMrzResultStore.clear();
		}
	}

	private void showMrzScanResult(@NonNull MrzScanResult result) {
		MrzData data = result.getData();
		if (data == null) {
			findViewById(R.id.result_view).setVisibility(View.GONE);
			TextView noResultView = findViewById(R.id.no_result_view);
			noResultView.setVisibility(View.VISIBLE);
			noResultView.setText("No MRZ data.");
			return;
		}

		findViewById(R.id.result_view).setVisibility(View.VISIBLE);
		findViewById(R.id.no_result_view).setVisibility(View.GONE);
		String genderText = data.getSex().substring(0, 1).toUpperCase() + data.getSex().substring(1).toLowerCase();

		TextView fullNameView = findViewById(R.id.tv_full_name);
		fullNameView.setText(data.getFirstName() + " " + data.getLastName());
		TextView genderAndAgeView = findViewById(R.id.tv_gender_and_age);
		genderAndAgeView.setText(genderText + ", " + data.getAge() + " years old");
		TextView expiryView = findViewById(R.id.tv_expiry);
		expiryView.setText("Expiry: " + data.getDateOfExpire());

		ImageView portraitView = findViewById(R.id.iv_portrait);
		ImageData portraitImage = result.getPortraitImage();
		if (portraitImage != null) {
			try {
				portraitView.setImageBitmap(portraitImage.toBitmap());
			} catch (CoreException ignored) {
			}
		} else {
			portraitView.setImageResource(R.drawable.ic_portrait_placeholder);
		}

		showImages(result);

		((TextView) findViewById(R.id.tv_given_name)).setText(data.getFirstName());
		((TextView) findViewById(R.id.tv_surname)).setText(data.getLastName());
		((TextView) findViewById(R.id.tv_date_of_birth)).setText(data.getDateOfBirth());
		((TextView) findViewById(R.id.tv_gender)).setText(genderText);
		((TextView) findViewById(R.id.tv_nationality)).setText(data.getNationality());

		TextView docTypeView = findViewById(R.id.tv_doc_type);
		switch (data.getDocumentType()) {
			case "MRTD_TD1_ID":
				docTypeView.setText("ID (TD1)");
				break;
			case "MRTD_TD2_ID":
				docTypeView.setText("ID (TD2)");
				break;
			case "MRTD_TD3_PASSPORT":
				docTypeView.setText("Passport (TD3)");
				break;
			default:
				docTypeView.setText(data.getDocumentType());
				break;
		}

		((TextView) findViewById(R.id.tv_doc_number)).setText(data.getDocumentNumber());
		((TextView) findViewById(R.id.tv_expiry_date)).setText(data.getDateOfExpire());
		((TextView) findViewById(R.id.tv_raw_mrz)).setText(data.getMrzText());
	}

	private void showImages(@NonNull MrzScanResult result) {
		ImageData mrzSideDocumentImage = result.getDocumentImage(EnumDocumentSide.DS_MRZ);
		ImageData oppositeSideDocumentImage = result.getDocumentImage(EnumDocumentSide.DS_OPPOSITE);
		ImageData mrzSideOriginalImage = result.getOriginalImage(EnumDocumentSide.DS_MRZ);
		ImageData oppositeSideOriginalImage = result.getOriginalImage(EnumDocumentSide.DS_OPPOSITE);

		ViewPager2 pager = findViewById(R.id.vp_images);
		ImageData primaryImage = mrzSideDocumentImage != null ? mrzSideDocumentImage : mrzSideOriginalImage;
		ImageData secondaryImage = mrzSideDocumentImage != null ? oppositeSideDocumentImage : oppositeSideOriginalImage;

		if (primaryImage == null) {
			pager.setVisibility(View.GONE);
			return;
		}

		pager.setVisibility(View.VISIBLE);
		pager.setAdapter(new FragmentStateAdapter(this) {
			@NonNull
			@Override
			public Fragment createFragment(int position) {
				return MrzImagesFragment.newInstance(primaryImage, secondaryImage);
			}

			@Override
			public int getItemCount() {
				return 1;
			}
		});
	}
}