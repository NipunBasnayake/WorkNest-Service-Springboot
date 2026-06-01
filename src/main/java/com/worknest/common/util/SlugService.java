package com.worknest.common.util;

import org.springframework.stereotype.Service;

@Service
public class SlugService {

    public String slugify(String name) {
        return SlugUtils.slugify(name);
    }
}
