package com.example.matsample.queries;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;

import java.util.*;

@Name("ThreadLocal Value Type Retained Stats")
@Category("Sample Extensions")
@Help("Stats of ThreadLocalMap$Entry entries with null keys grouped by value type and retained heap size")
public class ThreadLocalValueTypeQuery implements IQuery {
    @Argument
    public ISnapshot snapshot;

    @Override
    public IResult execute(IProgressListener listener) throws Exception {
        Map<String, Stats> statsMap = new HashMap<>();

        for (IClass cls : snapshot.getClassesByName(
            "java.lang.ThreadLocal$ThreadLocalMap$Entry", false)) {
            for (int objId : cls.getObjectIds()) {
                IObject entry = snapshot.getObject(objId);
                Object keyRef = entry.resolveValue("key");
                if (keyRef != null) continue;
                Object valRef = entry.resolveValue("value");
                if (!(valRef instanceof IObject)) continue;
                IObject value = (IObject) valRef;
                String type = value.getClazz().getName();
                Stats s = statsMap.computeIfAbsent(type, k -> new Stats());
                s.count++;
                s.retained += snapshot.getRetainedHeapSize(value.getObjectId());
            }
        }

        // 排序
        List<Map.Entry<String, Stats>> entries = new ArrayList<>(statsMap.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().retained, a.getValue().retained));

        // 构建表格文本
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-60s %10s %15s%n", "ValueType", "Count", "RetainedBytes"));
        sb.append(String.join("", Collections.nCopies(90, "-"))).append("\n");
        for (Map.Entry<String, Stats> e : entries) {
            sb.append(String.format("%-60s %10d %15d%n",
                e.getKey(), e.getValue().count, e.getValue().retained));
        }

        return new TextResult(sb.toString(), true);
    }

    static class Stats {
        int count = 0;
        long retained = 0;
    }
}
