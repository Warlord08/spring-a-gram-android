/*
 * Copyright 2014 Roy Clarkson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.royclarkson.springagram;

import android.app.Activity;
import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.royclarkson.springagram.model.ItemResource;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Fragment} that displays a list of {@link ItemResource}s
 *
 * @author Roy Clarkson
 */
public class PhotoListFragment extends Fragment implements AbsListView.OnItemClickListener {

	public static final String TAG = PhotoListFragment.class.getSimpleName();

	private static final String ARG_PHOTO_LIST_URL = "photos_url";

	private String photosUrl;

	private PhotoListFragmentListener photoListFragmentListener;

	private AbsListView listView;

	private ListAdapter listAdapter;


	public PhotoListFragment() {
		// Required empty public constructor
	}

	public static PhotoListFragment newInstance(String photosUrl) {
		PhotoListFragment fragment = new PhotoListFragment();
		Bundle args = new Bundle();
		args.putString(ARG_PHOTO_LIST_URL, photosUrl);
		fragment.setArguments(args);
		return fragment;
	}


	//***************************************
	// Fragment methods
	//***************************************

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			this.photosUrl = getArguments().getString(ARG_PHOTO_LIST_URL);
		}
		fetchPhotoList();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_photo, container, false);
		this.listView = (AbsListView) view.findViewById(android.R.id.list);
		this.listView.setOnItemClickListener(this);
		registerForContextMenu(this.listView);
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setRetainInstance(true);
	}

	/**
	 * The default content for this Fragment has a TextView that is shown when
	 * the list is empty. If you would like to change the text, call this method
	 * to supply the text it should use.
	 */
	public void setEmptyText(CharSequence emptyText) {
		View emptyView = listView.getEmptyView();

		if (emptyText instanceof TextView) {
			((TextView) emptyView).setText(emptyText);
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			this.photoListFragmentListener = (PhotoListFragmentListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement OnPhotoSelectedListener");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		this.photoListFragmentListener = null;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = new MenuInflater(this.getActivity());
		inflater.inflate(R.menu.photo_list_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
			case R.id.action_add_to_gallery:
				addToGallery(info.position);
				return true;
			case R.id.action_delete:
				deletePhoto(info.position);
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}


	//***************************************
	// OnItemClickListener methods
	//***************************************

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (null != this.photoListFragmentListener) {
			this.photoListFragmentListener.onPhotoSelected(position);
		}
	}


	//***************************************
	// Listener interface
	//***************************************

	public interface PhotoListFragmentListener {

		public void onDownloadPhotosComplete(List<ItemResource> photos);

		public void onPhotoSelected(int position);

		public ItemResource getPhotoByPosition(int position);

		public void onDeletePhotoByPosition(int position);

		public void onPhotoAddToGallerySelected(int position);

		public void onNetworkError(String message);

	}


	//***************************************
	// Private methods
	//***************************************

	public void fetchPhotoList() {
		new DownloadPhotosTask().execute(this.photosUrl);
	}

	private void refreshPhotoList(Resources resources) {
		if (resources != null) {
			List<ItemResource> photos = new ArrayList<ItemResource>(resources.getContent());
			if (null != this.photoListFragmentListener) {
				this.photoListFragmentListener.onDownloadPhotosComplete(photos);
			}
			listAdapter = new PhotoListAdapter(getActivity(), photos);
			listView.setAdapter(listAdapter);
		}
	}

	private void deletePhoto(int position) {
		ItemResource itemResource = this.photoListFragmentListener.getPhotoByPosition(position);
		new DeletePhotoTask().execute(itemResource.getLink(ItemResource.REL_SELF).getHref());
		this.photoListFragmentListener.onDeletePhotoByPosition(position);
		((PhotoListAdapter) listAdapter).notifyDataSetChanged();
	}

	private void addToGallery(int position) {
		this.photoListFragmentListener.onPhotoAddToGallerySelected(position);
	}


	//***************************************
	// Private classes
	//***************************************

	private class DownloadPhotosTask extends AsyncTask<String, Void, Resources> {

		private Exception exception;

		@Override
		protected Resources doInBackground(String... params) {
			try {
				final String url = params[0];
				ParameterizedTypeReference<Resources<ItemResource>> typeRef =
						new ParameterizedTypeReference<Resources<ItemResource>>() { };
				RestTemplate restTemplate = RestUtils.getInstance();
				ResponseEntity<Resources<ItemResource>> responseEntity = restTemplate.exchange(url, HttpMethod.GET,
						RestUtils.getRequestEntity(), typeRef);
				return responseEntity.getBody();
			} catch (Exception e) {
				this.exception = e;
				Log.e(TAG, e.getMessage(), e);
				return null;
			}
		}

		@Override
		protected void onPostExecute(Resources resources) {
			if (this.exception != null && this.exception instanceof ResourceAccessException) {
				photoListFragmentListener.onNetworkError(this.exception.getMessage());
			} else {
				refreshPhotoList(resources);
			}
		}

	}

	private class DeletePhotoTask extends AsyncTask<String, Void, Void> {

		Exception exception;

		@Override
		protected Void doInBackground(String... params) {
			try {
				final String url = params[0];
				RestTemplate restTemplate = RestUtils.getInstance();
				restTemplate.delete(url);
			} catch (Exception e) {
				this.exception = e;
				Log.e(TAG, e.getMessage(), e);
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			if (this.exception != null && this.exception instanceof ResourceAccessException) {
				photoListFragmentListener.onNetworkError(this.exception.getMessage());
			}
		}

	}

}
