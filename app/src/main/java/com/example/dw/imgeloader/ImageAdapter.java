package com.example.dw.imgeloader;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.example.dw.imgeloader.util.ImageLoader;
import com.example.dw.imgeloader.util.ImageLoader.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageAdapter extends BaseAdapter {
	private static Set<String> selectedImg = new HashSet<String>();

	private List<String> datas;
	// private Context context;
	private String dirPath;
	private LayoutInflater inflater;

	// ͼƬ�����ļ���·���ֿ��棬�ɼ��ٴ洢�ռ������
	public ImageAdapter(Context context, List<String> datas, String dirPath) {
		// this.context = context;
		this.datas = datas;
		this.dirPath = dirPath;

		this.inflater = LayoutInflater.from(context);
	}

	@Override
	public int getCount() {
		if (datas != null) {
			return datas.size();
		}
		return 0;
	}

	@Override
	public Object getItem(int position) {
		if (datas != null) {
			return datas.get(position);
		}
		return null;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		final ViewHolder viewHolder;

		if (convertView == null) {
			convertView = this.inflater.inflate(R.layout.item_gridview, parent,
					false);
			viewHolder = new ViewHolder();

			viewHolder.vhImage = (ImageView) convertView
					.findViewById(R.id.id_item_image);
			viewHolder.vhButton = (ImageButton) convertView
					.findViewById(R.id.id_item_select);

			convertView.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) convertView.getTag();
		}

		// ͼƬ״̬����
		viewHolder.vhImage.setImageResource(R.drawable.picture_no);
		viewHolder.vhButton.setImageResource(R.drawable.picture_unselected);
		viewHolder.vhImage.setColorFilter(null);

		final String imgPath = dirPath + "/" + datas.get(position);

		ImageLoader.getInstance(3, Type.LIFO).loadImage(imgPath,
				viewHolder.vhImage);

		viewHolder.vhImage.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// �Ѿ���ѡ��
				if (selectedImg.contains(imgPath)) {
					selectedImg.remove(imgPath);
					viewHolder.vhImage.setColorFilter(null);
					viewHolder.vhButton.setImageResource(R.drawable.picture_unselected);
				}
				// δ��ѡ��
				else {
					selectedImg.add(imgPath);
					viewHolder.vhImage.setColorFilter(Color.parseColor("#77000000"));
					viewHolder.vhButton.setImageResource(R.drawable.picture_selected);
				}
				//notify���������
//				notifyDataSetChanged();
			}
		});

		if (selectedImg.contains(imgPath)) {
			viewHolder.vhImage.setColorFilter(Color.parseColor("#77000000"));
			viewHolder.vhButton.setImageResource(R.drawable.picture_selected);
		}

		return convertView;
	}

	private class ViewHolder {
		ImageView vhImage;
		ImageButton vhButton;
	}
}