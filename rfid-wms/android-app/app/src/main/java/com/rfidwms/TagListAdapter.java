package com.rfidwms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RFID 标签列表适配器
 * 显示：EPC、TID、名称、类别、数量、存放位置、扫描时间
 */
public class TagListAdapter extends ArrayAdapter<MainActivity.TagItem> {

    private final LayoutInflater inflater;
    private List<MainActivity.TagItem> items;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    public TagListAdapter(Context context, List<MainActivity.TagItem> items) {
        super(context, R.layout.item_tag, items);
        this.inflater = LayoutInflater.from(context);
        this.items = items;
    }

    public void updateData(List<MainActivity.TagItem> newItems) {
        this.items = newItems;
        clear();
        addAll(newItems);
        notifyDataSetChanged();
    }

    public List<MainActivity.TagItem> getItems() {
        return items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_tag, parent, false);
            holder = new ViewHolder();
            holder.tvEpc = convertView.findViewById(R.id.tvItemEpc);
            holder.tvTid = convertView.findViewById(R.id.tvItemTid);
            holder.layoutTid = convertView.findViewById(R.id.layoutTid);
            holder.tvTime = convertView.findViewById(R.id.tvItemTime);
            holder.tvName = convertView.findViewById(R.id.tvItemName);
            holder.tvCategory = convertView.findViewById(R.id.tvItemCategory);
            holder.tvQty = convertView.findViewById(R.id.tvItemQty);
            holder.tvLocation = convertView.findViewById(R.id.tvItemLocation);
            holder.layoutDeptUser = convertView.findViewById(R.id.layoutDeptUser);
            holder.tvDepartment = convertView.findViewById(R.id.tvItemDepartment);
            holder.tvUserName = convertView.findViewById(R.id.tvItemUserName);
            holder.tvPurchaseDate = convertView.findViewById(R.id.tvItemPurchaseDate);
            holder.layoutUserData = convertView.findViewById(R.id.layoutUserData);
            holder.tvUserData = convertView.findViewById(R.id.tvItemUserData);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        MainActivity.TagItem item = items.get(position);

        holder.tvEpc.setText(item.epc);
        holder.tvTime.setText(sdf.format(new Date(item.scanTime)));

        // TID 显示（用于区分同 EPC 的标签）
        if (item.tid != null && !item.tid.isEmpty()) {
            holder.layoutTid.setVisibility(View.VISIBLE);
            holder.tvTid.setText(item.tid);
        } else {
            holder.layoutTid.setVisibility(View.GONE);
        }

        holder.tvName.setText(item.name.isEmpty() ? "（未命名）" : item.name);
        holder.tvCategory.setText(item.category.isEmpty() ? "-" : item.category);
        holder.tvQty.setText(String.valueOf(item.qty));
        holder.tvLocation.setText(item.location.isEmpty() ? "未设置位置" : item.location);

        // 部门 + 使用人 + 入库日期
        boolean hasDeptInfo = !item.department.isEmpty() || !item.userName.isEmpty() || !item.purchaseDate.isEmpty();
        if (hasDeptInfo) {
            holder.layoutDeptUser.setVisibility(View.VISIBLE);
            holder.tvDepartment.setText(item.department.isEmpty() ? "" : item.department);
            holder.tvUserName.setText(item.userName.isEmpty() ? "" : item.userName);
            holder.tvPurchaseDate.setText(item.purchaseDate.isEmpty() ? "" : item.purchaseDate);
        } else {
            holder.layoutDeptUser.setVisibility(View.GONE);
        }

        // User Memory 数据
        if (item.userData != null && !item.userData.isEmpty()) {
            holder.layoutUserData.setVisibility(View.VISIBLE);
            holder.tvUserData.setText(item.userData);
        } else {
            holder.layoutUserData.setVisibility(View.GONE);
        }

        // 已同步的标签用绿色 EPC 显示，未同步的用蓝色
        if (item.synced) {
            holder.tvEpc.setTextColor(0xFF34D399);
        } else {
            holder.tvEpc.setTextColor(0xFF38BDF8);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvEpc;
        TextView tvTid;
        LinearLayout layoutTid;
        TextView tvTime;
        TextView tvName;
        TextView tvCategory;
        TextView tvQty;
        TextView tvLocation;
        LinearLayout layoutDeptUser;
        TextView tvDepartment;
        TextView tvUserName;
        TextView tvPurchaseDate;
        LinearLayout layoutUserData;
        TextView tvUserData;
    }
}
