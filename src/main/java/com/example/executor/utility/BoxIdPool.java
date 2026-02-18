package com.example.executor.utility;

import com.example.executor.constants.ExecutorConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe pool for managing isolate box IDs.
 * Ensures no two concurrent executions use the same box ID.
 */
@Slf4j
@Component
public class BoxIdPool {

    private final BlockingQueue<String> availableBoxIds;

    public BoxIdPool() {
        availableBoxIds = new LinkedBlockingQueue<>();
        // Initialize pool with all available box IDs
        for (int i = 0; i < ExecutorConstants.MAX_BOX_ID; i++) {
            availableBoxIds.offer(String.valueOf(i));
        }
        log.info("Initialized BoxIdPool with {} box IDs", ExecutorConstants.MAX_BOX_ID);
    }

    /**
     * Acquire a box ID from the pool.
     * Blocks if no box IDs are available until one is returned.
     *
     * @return an available box ID
     * @throws InterruptedException if interrupted while waiting
     */
    public String acquire() throws InterruptedException {
        String boxId = availableBoxIds.poll(30, TimeUnit.SECONDS);
        if (boxId == null) {
            throw new RuntimeException("Timeout waiting for available box ID - all boxes busy");
        }
        log.debug("Acquired box ID: {}", boxId);
        return boxId;
    }

    /**
     * Release a box ID back to the pool for reuse.
     *
     * @param boxId the box ID to return
     */
    public void release(String boxId) {
        availableBoxIds.offer(boxId);
        log.debug("Released box ID: {}", boxId);
    }

    /**
     * Get the number of available box IDs.
     *
     * @return number of available box IDs
     */
    public int getAvailableCount() {
        return availableBoxIds.size();
    }
}
