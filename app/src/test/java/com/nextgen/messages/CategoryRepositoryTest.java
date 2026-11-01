package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.data.model.CategoryItem;
import com.nextgen.messages.data.repository.CategoryRepository;
import java.util.List;
import org.junit.Test;

public class CategoryRepositoryTest {

    @Test
    public void testCategoriesListIsNotEmpty() {
        CategoryRepository repository = CategoryRepository.getInstance();
        List<CategoryItem> categories = repository.getCategories();

        assertNotNull("Categories list should not be null", categories);
        assertEquals("Expected 5 planned smart categories", 5, categories.size());

        for (CategoryItem item : categories) {
            assertNotNull("Category id must not be null", item.getId());
            assertFalse("Category id must not be empty", item.getId().isEmpty());
            assertTrue("Title resource must be valid", item.getTitleRes() != 0);
            assertTrue("Description resource must be valid", item.getDescRes() != 0);
            assertTrue("Icon resource must be valid", item.getIconRes() != 0);
            assertTrue("Badge resource must be valid", item.getBadgeRes() != 0);
        }
    }

    @Test
    public void testExpectedCategoriesPresent() {
        CategoryRepository repository = CategoryRepository.getInstance();
        List<CategoryItem> categories = repository.getCategories();

        boolean hasPersonal = false;
        boolean hasTransactions = false;
        boolean hasOtp = false;
        boolean hasPromotions = false;
        boolean hasSpam = false;

        for (CategoryItem item : categories) {
            if ("personal".equals(item.getId())) hasPersonal = true;
            if ("transactions".equals(item.getId())) hasTransactions = true;
            if ("otp".equals(item.getId())) hasOtp = true;
            if ("promotions".equals(item.getId())) hasPromotions = true;
            if ("spam".equals(item.getId())) hasSpam = true;
        }

        assertTrue("Must include personal category", hasPersonal);
        assertTrue("Must include transactions category", hasTransactions);
        assertTrue("Must include OTP category", hasOtp);
        assertTrue("Must include promotions category", hasPromotions);
        assertTrue("Must include spam category", hasSpam);
    }
}
