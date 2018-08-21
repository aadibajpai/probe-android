package org.openobservatory.ooniprobe.fragment;

import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Method;
import com.raizlabs.android.dbflow.sql.language.SQLOperator;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.openobservatory.ooniprobe.R;
import org.openobservatory.ooniprobe.activity.ResultDetailActivity;
import org.openobservatory.ooniprobe.item.DateItem;
import org.openobservatory.ooniprobe.item.InstantMessagingItem;
import org.openobservatory.ooniprobe.item.MiddleboxesItem;
import org.openobservatory.ooniprobe.item.PerformanceItem;
import org.openobservatory.ooniprobe.item.WebsiteItem;
import org.openobservatory.ooniprobe.model.Measurement;
import org.openobservatory.ooniprobe.model.Measurement_Table;
import org.openobservatory.ooniprobe.model.Network;
import org.openobservatory.ooniprobe.model.Result;
import org.openobservatory.ooniprobe.model.Result_Table;
import org.openobservatory.ooniprobe.test.TestSuite;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnItemSelected;
import localhost.toolkit.app.ConfirmDialogFragment;
import localhost.toolkit.widget.HeterogeneousRecyclerAdapter;
import localhost.toolkit.widget.HeterogeneousRecyclerItem;

public class ResultListFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener, ConfirmDialogFragment.OnConfirmedListener {
	@BindView(R.id.toolbar) Toolbar toolbar;
	@BindView(R.id.tests) TextView tests;
	@BindView(R.id.networks) TextView networks;
	@BindView(R.id.upload) TextView upload;
	@BindView(R.id.download) TextView download;
	@BindView(R.id.filterTests) Spinner filterTests;
	@BindView(R.id.recycler) RecyclerView recycler;
	private ArrayList<HeterogeneousRecyclerItem> items;
	private HeterogeneousRecyclerAdapter<HeterogeneousRecyclerItem> adapter;

	@Nullable @Override public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_result_list, container, false);
		ButterKnife.bind(this, v);
		((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
		setHasOptionsMenu(true);
		getActivity().setTitle(R.string.TestResults_Overview_Title);
		tests.setText(getString(R.string.d, SQLite.selectCountOf().from(Result.class).longValue()));
		networks.setText(getString(R.string.d, SQLite.selectCountOf().from(Network.class).longValue()));
		upload.setText(getString(R.string.d, SQLite.select(Method.sum(Result_Table.data_usage_up)).from(Result.class).longValue()));
		download.setText(getString(R.string.d, SQLite.select(Method.sum(Result_Table.data_usage_down)).from(Result.class).longValue()));
		LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
		recycler.setLayoutManager(layoutManager);
		recycler.addItemDecoration(new DividerItemDecoration(getActivity(), layoutManager.getOrientation()));
		items = new ArrayList<>();
		adapter = new HeterogeneousRecyclerAdapter<>(getActivity(), items);
		recycler.setAdapter(adapter);
		return v;
	}

	@Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.delete, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.delete:
				ConfirmDialogFragment.newInstance(null, getString(R.string.app_name), getString(R.string.Modal_DoYouWantToDeleteResults)).show(getChildFragmentManager(), null);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@OnItemSelected(R.id.filterTests) public void queryList() {
		HashSet<Integer> set = new HashSet<>();
		items.clear();
		ArrayList<SQLOperator> where = new ArrayList<>();
		String filter = getResources().getStringArray(R.array.filterTestValues)[filterTests.getSelectedItemPosition()];
		if (!filter.isEmpty())
			where.add(Result_Table.test_group_name.is(filter));
		for (Result result : SQLite.select().from(Result.class).where(where.toArray(new SQLOperator[where.size()])).orderBy(Result_Table.start_time, false).queryList()) {
			Calendar c = Calendar.getInstance();
			c.setTime(result.start_time);
			int key = c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH);
			if (!set.contains(key)) {
				items.add(new DateItem(result.start_time));
				set.add(key);
			}
			switch (result.test_group_name) {
				case TestSuite.WEBSITES:
					items.add(new WebsiteItem(result, this, this));
					break;
				case TestSuite.INSTANT_MESSAGING:
					items.add(new InstantMessagingItem(result, this, this));
					break;
				case TestSuite.MIDDLE_BOXES:
					items.add(new MiddleboxesItem(result, this, this));
					break;
				case TestSuite.PERFORMANCE:
					items.add(new PerformanceItem(result, this, this));
					break;
			}
		}
		adapter.notifyTypesChanged();
	}

	@Override public void onClick(View v) {
		Result result = (Result) v.getTag();
		startActivity(ResultDetailActivity.newIntent(getActivity(), result.id));
	}

	@Override public boolean onLongClick(View v) {
		Result result = (Result) v.getTag();
		ConfirmDialogFragment.newInstance(result, getString(R.string.app_name), "stringa mancante").show(getChildFragmentManager(), null);
		return true;
	}

	@Override public void onConfirmation(Serializable serializable, int i) {
		if (i == DialogInterface.BUTTON_POSITIVE) {
			if (serializable == null) {
				Delete.tables(Measurement.class, Result.class);
			} else {
				Result result = (Result) serializable;
				SQLite.delete().from(Measurement.class).where(Measurement_Table.result_id.eq(result.id)).execute();
				result.delete();
			}
			queryList();
		}
	}
}