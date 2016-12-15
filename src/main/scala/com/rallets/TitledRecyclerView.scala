package com.rallets

import android.content.Context
import android.util.{AttributeSet, Log, TypedValue}
import android.os.{AsyncTask, Bundle, Handler}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget._
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget._
import android.support.v7.widget.helper.ItemTouchHelper
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback
import java.util

import android.content.res.TypedArray
import com.github.rallets.{R, ToolbarFragment}
import com.rallets.TitledRecyclerView._

object TitledRecyclerView {

  trait OnItemClickListener {
    def onItemClick(item: Item, position: Int)
  }

  class Item(
    var icon: Integer,
    var mainText: String,
    var subText: String,
    var showArrow: Boolean
  ) {}

}

class TitledRecyclerView(context: Context, attrs: AttributeSet) extends LinearLayout(context, attrs) {

  private var titleView: TextView = _
  private var recyclerView: RecyclerView = _
  private var recyclerViewAdapter: MyAdapter = _
  private var recyclerViewLayoutManager: RecyclerView.LayoutManager = _
  private var recyclerViewOnItemClickListener: OnItemClickListener = new OnItemClickListener {
    override def onItemClick(item: Item, position: Int): Unit = {}
  }
  private var recyclerViewDataSet: Array[Item] = Array()
  private var title: String = _
  private var showArrow: Boolean = false
  private var listPadding: Float = _
  private var listTextSize: Float = _
  init()

  def init(): Unit = {
    View.inflate(context, R.layout.layout_titled_recycler_view, this)
    titleView = findViewById(R.id.title).asInstanceOf[TextView]
    recyclerView = findViewById(R.id.recyclerView).asInstanceOf[RecyclerView]
    // apply attrs
    val a =  context.getTheme().obtainStyledAttributes(attrs, R.styleable.TitledRecyclerView, 0, 0)
    try {
      title = a.getString(R.styleable.TitledRecyclerView_title)
      showArrow = a.getBoolean(R.styleable.TitledRecyclerView_showArrow, false)
      listPadding = a.getDimension(R.styleable.TitledRecyclerView_listPadding, 0)
      listTextSize = a.getDimension(R.styleable.TitledRecyclerView_listTextSize, 0)
    } finally {
      a.recycle()
    }
    if (title != null) {
      setTitle(title)
    }
    recyclerViewLayoutManager = new LinearLayoutManager(getContext)
    recyclerView.setLayoutManager(recyclerViewLayoutManager)

    recyclerViewAdapter = new MyAdapter(recyclerViewDataSet)
    recyclerView.setAdapter(recyclerViewAdapter)
  }

  def getRecyclerView(): RecyclerView = recyclerView
  def getTitle(): String = title
  def setOnItemClickListener(l: OnItemClickListener): Unit = recyclerViewOnItemClickListener = l
  def setDataSet(d: Array[Item]): Unit = recyclerViewAdapter.updateDataSet(d)
  def setTitle(title: String): Unit = {
    this.title = title
    titleView.setText(title)
    titleView.setVisibility(View.VISIBLE)
  }
  def setTitle(title: Int): Unit = {
    setTitle(getContext.getResources.getString(title))
  }
  def notifyDataSetChanged: Unit = recyclerViewAdapter.notifyDataSetChanged()

  object MyAdapter {
    class ViewHolder(var mView: ViewGroup) extends RecyclerView.ViewHolder(mView)
  }
  class MyAdapter // Provide a suitable constructor (depends on the kind of dataset)
  (private var mDataset: Array[Item])
    extends RecyclerView.Adapter[MyAdapter.ViewHolder] {

    def updateDataSet(d: Array[Item]): Unit = {
      mDataset = d
      notifyDataSetChanged()
    }

    // Create new views (invoked by the layout manager)
    override def onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): MyAdapter.ViewHolder = {
      // create a new view
      val v: ViewGroup = LayoutInflater
        .from(parent.getContext)
        .inflate(R.layout.layout_titled_recycler_item, parent, false).asInstanceOf[ViewGroup]
      // set the view's size, margins, paddings and layout parameters

      val vh: MyAdapter.ViewHolder = new MyAdapter.ViewHolder(v)
      vh
    }

    // Replace the contents of a view (invoked by the layout manager)
    override def onBindViewHolder(holder: MyAdapter.ViewHolder, position: Int): Unit = {
      // - replace the contents of the view with that element
      val v = holder.mView
      if (listPadding != 0) {
        v.setPadding(listPadding.toInt, listPadding.toInt, listPadding.toInt, listPadding.toInt)
      }
      val icon = v.findViewById(R.id.icon).asInstanceOf[ImageView]
      val mainText = v.findViewById(R.id.mainText).asInstanceOf[TextView]
      if (listTextSize != 0) {
        mainText.setTextSize(TypedValue.COMPLEX_UNIT_PX, listTextSize)
      }
      val subText = v.findViewById(R.id.subText).asInstanceOf[TextView]
      if (listTextSize != 0) {
        subText.setTextSize(TypedValue.COMPLEX_UNIT_PX, listTextSize)
      }
      val arrow = v.findViewById(R.id.arrow).asInstanceOf[ImageView]
      val d = mDataset(position)
      if (d.icon != null) {
        icon.setImageResource(d.icon)
      } else {
        icon.setVisibility(View.GONE)
      }
      mainText.setText(d.mainText)
      subText.setText(d.subText)
      if (d.showArrow) {
        arrow.setVisibility(View.VISIBLE)
      } else {
        arrow.setVisibility(View.INVISIBLE)
      }
      // bind onclick
      v.setOnClickListener(new View.OnClickListener {
        override def onClick(v: View): Unit = {
          if (recyclerViewOnItemClickListener != null) {
            recyclerViewOnItemClickListener.onItemClick(d, position)
          }
        }
      })
    }

    // Return the size of your dataset (invoked by the layout manager)
    override def getItemCount(): Int = mDataset.length

  }
}
