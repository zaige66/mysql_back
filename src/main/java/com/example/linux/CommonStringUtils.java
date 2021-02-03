package com.example.linux;

import java.util.Collection;

/**
 * @author kangxuan
 * @date 2019/6/13 0013 11:42.
 * @Description:
 */
public class CommonStringUtils {

    /**
     * 将数组用分隔符拼接
     *
     * @param arr       需要转换的数组
     * @param delimiter 分割符
     * @return
     */
    public static final String concatArr2String(Collection arr, String delimiter) {
        StringBuffer buffer = new StringBuffer();

        for (Object o : arr) {
            buffer.append(o).append(delimiter);
        }

        String substring = buffer.substring(0, buffer.length() - 1);
        return substring;
    }
}
