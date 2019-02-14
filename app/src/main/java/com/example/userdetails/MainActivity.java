package com.example.userdetails;

import android.app.SearchManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import com.example.userdetails.model.Results;
import com.example.userdetails.model.Users;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import io.realm.OrderedRealmCollection;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmList;
import io.realm.RealmResults;
import io.realm.Sort;

public class MainActivity extends AppCompatActivity implements CustomAdapter.CustomAdapterListener {

    Gson gson;
    String jsonResults;
    Users users;
    List<Results> unmanagedResults;
    CustomRealmAdapter adapter;
    RecyclerView recyclerView;
    SearchView searchView;
    File dir;
    Snackbar snackbar;
    Realm realm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        users = new Users();

        dir = getExternalFilesDir("userdetails/db");

        Realm.init(this);
        RealmConfiguration config = new RealmConfiguration.Builder().name("usersrealm.realm").
                directory(dir).build();
        Realm.setDefaultConfiguration(config);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        getSupportActionBar().setTitle(R.string.app_name);

        setupRecyclerView();
        //Open list from DB
        //startupList();

        //Pull to refresh
        final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
        pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                ConnectivityManager cm =
                        (ConnectivityManager) getBaseContext()
                                .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null &&
                        activeNetwork.isConnectedOrConnecting();
                if (isConnected == false) {
                    snackbar = Snackbar.make(findViewById(android.R.id.content),
                            R.string.offline, Snackbar.LENGTH_LONG);
                    snackbar.show();
                } else
                    new GsonDeserializer().execute();
                pullToRefresh.setRefreshing(false);
            }
        });
    }

    @Override
    public void onSelected(String item) {

    }

    class GsonDeserializer extends AsyncTask<String, Void, Users> {

        @Override
        protected void onPreExecute() {
            gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        }

        @Override
        protected Users doInBackground(String... strings) {

            Realm realm = Realm.getDefaultInstance();
            Users results = new Users();

            try {
                URL url = new URL("https://randomuser.me/api/?results=10");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] contents = new byte[1024];
                    int bytesRead = 0;
                    jsonResults = "";
                    while ((bytesRead = in.read(contents)) != -1) {
                        jsonResults += new String(contents, 0, bytesRead);
                    }
                    Type resultType = new TypeToken<Users>() {
                    }.getType();
                    results = gson.fromJson(jsonResults, resultType);
                } finally {
                    urlConnection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            final Users finalResults = results;
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    for(Results result : finalResults.getResults()) {
                        Number currentIdNum = realm.where(Results.class)
                                .max("dbId");
                        int nextId;
                        if(currentIdNum == null) {
                            nextId = 1;
                        } else {
                            nextId = currentIdNum.intValue() + 1;
                        }

                            result.setDbId(nextId);
                            realm.insert(result);
                    }
                }
            });
            return results;
        }

        @Override
        protected void onPostExecute(Users results) {
            users = new Users();
            users.setResults(new ArrayList<>());
            Realm realm = Realm.getDefaultInstance();
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    RealmResults<Results> queryResults;
                    queryResults = realm
                            .where(Results.class)
                            .findAll().sort("dbId", Sort.DESCENDING);
                    users.getResults().addAll(queryResults);
                    adapter = new CustomRealmAdapter
                            (queryResults, true);
                }
            });
            //unmanagedResults = realm.copyFromRealm(users.getResults());
            realm.close();
            recyclerView.setAdapter(adapter);
            //adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menu.findItem(R.id.action_search)
                .getActionView();
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(getComponentName()));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                adapter.getFilter().filter(query);
                return false;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!searchView.isIconified()) {
            searchView.setIconified(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recyclerView.setAdapter(null);
        realm.close();
    }

    private void setupRecyclerView() {
        realm = Realm.getDefaultInstance();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomRealmAdapter(realm.where(Results.class).findAll(), true);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
    }

}
