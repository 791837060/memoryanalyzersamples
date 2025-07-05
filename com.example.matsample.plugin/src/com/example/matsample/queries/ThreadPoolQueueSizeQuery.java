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
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.IProgressListener;

import java.util.*;

@Name("ThreadPool Queue Size Statistics")
@Category("ThreadPool Analysis")
@Help("Count queued tasks in ThreadPoolExecutor workQueue, sorted descending by queue size")
public class ThreadPoolQueueSizeQuery implements IQuery {

    @Argument
    public ISnapshot snapshot;

    @Override
    public IResult execute(IProgressListener listener) throws Exception {
        StringBuilder sb = new StringBuilder();

        List<ThreadPoolInfo> pools = new ArrayList<>();

        Collection<IClass> poolClasses = snapshot.getClassesByName("java.util.concurrent.ThreadPoolExecutor", false);
        if (poolClasses == null || poolClasses.isEmpty()) {
            return new TextResult("No ThreadPoolExecutor found", true);
        }

        IClass threadPoolClass = poolClasses.iterator().next();

        for (int objId : threadPoolClass.getObjectIds()) {
            IObject obj = snapshot.getObject(objId);
            IObject workQueue = (IObject) obj.resolveValue("workQueue");
            if (workQueue == null) continue;

            int queueSize = getQueueSize(workQueue);

            pools.add(new ThreadPoolInfo(obj.getTechnicalName(), queueSize, workQueue.getClazz().getName()));
        }

        // 按 Queue Size 倒序排序
        pools.sort(Comparator.comparingInt((ThreadPoolInfo p) -> -p.queueSize));

        // 构造输出
        sb.append(String.format("%-40s %-10s %-40s\n", "ThreadPoolExecutor", "QueueSize", "WorkQueueType"));

        for (int i = 0; i < 100; i++) {
            sb.append("=");
        }
        sb.append("\n");


        for (ThreadPoolInfo info : pools) {
            sb.append(String.format("%-40s %-10d %-40s\n",
                info.address, info.queueSize, info.workQueueType));
        }

        return new TextResult(sb.toString(), true);
    }

    private int getQueueSize(IObject workQueue) {
        try {
            String queueClass = workQueue.getClazz().getName();

            // LinkedBlockingQueue：通过 head -> next 链表遍历
            if (queueClass.contains("LinkedBlockingQueue")) {
                IObject head = (IObject) workQueue.resolveValue("head");
                if (head == null) return -1;

                int count = 0;
                Set<Integer> visited = new HashSet<>();

                while (head != null && !visited.contains(head.getObjectId())) {
                    visited.add(head.getObjectId());
                    count++;

                    Object next = head.resolveValue("next");
                    if (next instanceof IObject) {
                        head = (IObject) next;
                    } else {
                        break;
                    }
                }

                return Math.max(0, count - 1); // head 是 dummy 节点
            }

            // ArrayBlockingQueue：有 count 字段
            if (queueClass.contains("ArrayBlockingQueue")) {
                Object count = workQueue.resolveValue("count");
                if (count instanceof Number) {
                    return ((Number) count).intValue();
                }
            }

            // DelayQueue / PriorityQueue：有 queue 字段是 Object[]
            if (queueClass.contains("DelayQueue") || queueClass.contains("PriorityQueue")) {
                Object queue = workQueue.resolveValue("queue");
                if (queue instanceof IObjectArray) {
                    return ((IObjectArray) queue).getLength();
                }
            }

            // SynchronousQueue：几乎不缓存任务，默认返回 0
            if (queueClass.contains("SynchronousQueue")) {
                return 0;
            }

        } catch (Exception ignored) {
        }
        return -1; // 无法识别或失败
    }



    private static class ThreadPoolInfo {
        String address;
        int queueSize;
        String workQueueType;

        ThreadPoolInfo(String address, int queueSize, String workQueueType) {
            this.address = address;
            this.queueSize = queueSize;
            this.workQueueType = workQueueType;
        }
    }
}
