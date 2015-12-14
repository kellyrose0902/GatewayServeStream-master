package com.appfactory.quinn.m3ustreamtest2;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;

import com.example.quinn.m3ustreamtest2.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Kelly on 12/14/2015.
 */
public class AnimatedAdapter extends AnimatedExpandableListView.AnimatedExpandableListAdapter {
    private LayoutInflater inflater;
    public static class GroupItem {
        String title;
        List<ChildItem> items = new ArrayList<ChildItem>();
    }

    public static class ChildItem {
        String title;

    }

    public static class ChildHolder {
        TextView title;

    }

    public static class GroupHolder {
        TextView title;
    }

    public List<GroupItem> items;

    public AnimatedAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setData(List<GroupItem> items) {
        this.items = items;
    }

    @Override
    public ChildItem getChild(int groupPosition, int childPosition) {
        return items.get(groupPosition).items.get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getRealChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        ChildHolder holder;
        ChildItem item = getChild(groupPosition, childPosition);
        if (convertView == null) {
            holder = new ChildHolder();
            convertView = inflater.inflate(R.layout.list_item, parent, false);
            holder.title = (TextView) convertView.findViewById(R.id.list_item_text);

            convertView.setTag(holder);
        } else {
            holder = (ChildHolder) convertView.getTag();
        }

        holder.title.setText(item.title);

        ScaleAnimation(convertView, item.title);
        return convertView;
    }

    private void ScaleAnimation(View convertView, String childText) {
        if(childText.compareTo("Jazz")==0){


            ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1f);
            anim1.setDuration(100);
            convertView.startAnimation(anim1);


        }

        if(childText.compareTo("Rock")==0){


            ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1f);
            anim1.setDuration(100);
            anim1.setStartOffset(50);
            convertView.startAnimation(anim1);

        }

        if(childText.compareTo("Country")==0){

            ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1);
            anim1.setDuration(100);
            anim1.setStartOffset(150);
            convertView.startAnimation(anim1);
        }
        if(childText.compareTo("Indie")==0){

            ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1);
            anim1.setDuration(100);
            anim1.setStartOffset(250);
            convertView.startAnimation(anim1);
        }
        if(childText.compareTo("Pop")==0){

            ScaleAnimation anim1 = new ScaleAnimation(1, 1, 0, 1);
            anim1.setDuration(100);
            anim1.setStartOffset(350);
            convertView.startAnimation(anim1);
        }
    }

    @Override
    public int getRealChildrenCount(int groupPosition) {
        return items.get(groupPosition).items.size();
    }

    @Override
    public GroupItem getGroup(int groupPosition) {
        return items.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return items.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        GroupHolder holder;
        GroupItem item = getGroup(groupPosition);
        if (convertView == null) {
            holder = new GroupHolder();
            convertView = inflater.inflate(R.layout.group_item, parent, false);
            holder.title = (TextView) convertView.findViewById(R.id.listHeader);
            convertView.setTag(holder);
        } else {
            holder = (GroupHolder) convertView.getTag();
        }

        holder.title.setText(item.title);


        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int arg0, int arg1) {
        return true;
    }

}

