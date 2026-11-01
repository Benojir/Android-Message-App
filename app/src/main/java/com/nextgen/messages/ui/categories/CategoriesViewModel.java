package com.nextgen.messages.ui.categories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.nextgen.messages.data.model.CategoryItem;
import com.nextgen.messages.data.repository.CategoryRepository;
import java.util.List;

/**
 * ViewModel managing the Categories state and loading roadmap items.
 */
public class CategoriesViewModel extends ViewModel {

    private final MutableLiveData<List<CategoryItem>> categories = new MutableLiveData<>();
    private final CategoryRepository repository;

    public CategoriesViewModel() {
        this.repository = CategoryRepository.getInstance();
        loadCategories();
    }

    private void loadCategories() {
        categories.setValue(repository.getCategories());
    }

    public LiveData<List<CategoryItem>> getCategories() {
        return categories;
    }
}
