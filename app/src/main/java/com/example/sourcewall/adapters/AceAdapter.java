package com.example.sourcewall.adapters;

import android.content.Context;
import android.widget.BaseAdapter;

import java.util.ArrayList;

/**
 * Created by NashLegend on 2014/9/18 0018
 */
public abstract class AceAdapter<T> extends BaseAdapter {

    public final ArrayList<T> list = new ArrayList<>();
    private Context context;

    public AceAdapter(Context context) {
        setContext(context);
    }

    public void addAll(ArrayList<T> list) {
        this.list.addAll(list);
    }

    public void clear() {
        list.clear();
    }

    public void add(T t) {
        list.add(t);
    }

    public void add(int index, T t) {
        list.add(index, t);
    }

    public void remove(T t) {
        list.remove(t);
    }

    public void remove(int index) {
        list.remove(index);
    }

    public ArrayList<T> getList() {
        return list;
    }

    public void setList(ArrayList<T> list) {
        this.list.clear();
        this.list.addAll(list);
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
