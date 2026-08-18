import { randomUUID } from 'node:crypto';
import { getEnv } from '../src/config/env';

interface SmokeContext {
  baseUrl: string;
  email: string;
  token: string;
  userId: string;
  today: string;
  createdTaskIds: string[];
}

function formatDate(date: Date): string {
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

async function apiRequest<T = any>(
  ctx: Partial<SmokeContext>,
  path: string,
  options: {
    method?: string;
    body?: any;
    token?: string;
    expectedStatus?: number;
  } = {}
): Promise<{ status: number; data: T }> {
  const method = options.method || 'GET';
  const expectedStatus = options.expectedStatus ?? 200;
  const headers: Record<string, string> = {};

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.token) {
    headers['Authorization'] = `Bearer ${options.token}`;
  }

  const url = `${ctx.baseUrl || 'http://127.0.0.1:3000'}${path}`;
  const response = await fetch(url, {
    method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  let data: any = null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    data = await response.json();
  } else if (response.status !== 204) {
    data = await response.text();
  }

  if (response.status !== expectedStatus) {
    const errorMsg =
      data?.error?.message ||
      data?.message ||
      (typeof data === 'string' && data.length > 0 ? data : `Status ${response.status}`);
    throw new Error(`Request ${method} ${path} failed with HTTP ${response.status} (expected ${expectedStatus}): ${errorMsg}`);
  }

  return { status: response.status, data };
}

export async function runSmokeTest(): Promise<void> {
  const email = getEnv('TEST_USER_EMAIL');
  const password = getEnv('TEST_USER_PASSWORD');
  const baseUrl = process.env.API_BASE_URL || `http://127.0.0.1:${process.env.PORT || 3000}`;
  const today = formatDate(new Date());

  const ctx: SmokeContext = {
    baseUrl,
    email,
    token: '',
    userId: '',
    today,
    createdTaskIds: [],
  };

  console.log(`Starting API smoke test against ${baseUrl}...\n`);

  try {
    // 1. Login
    const loginRes = await apiRequest(ctx, '/auth/login', {
      method: 'POST',
      body: { email, password },
      expectedStatus: 200,
    });
    if (!loginRes.data?.token || !loginRes.data?.user?.id) {
      throw new Error('Login response missing token or user id');
    }
    ctx.token = loginRes.data.token;
    ctx.userId = loginRes.data.user.id;
    console.log('✓ Login');

    // 2. Verify /auth/me
    const meRes = await apiRequest(ctx, '/auth/me', {
      token: ctx.token,
      expectedStatus: 200,
    });
    if (meRes.data?.user?.id !== ctx.userId) {
      throw new Error('/auth/me user ID does not match login user');
    }
    console.log('✓ Authenticated user');

    // 3. Create Later Task
    const laterTaskId = randomUUID();
    ctx.createdTaskIds.push(laterTaskId);
    await apiRequest(ctx, '/tasks', {
      method: 'POST',
      token: ctx.token,
      body: {
        id: laterTaskId,
        title: 'Smoke Test Later Task',
        isImportant: false,
      },
      expectedStatus: 201,
    });
    console.log('✓ Create task');

    // 4. Verify Later View
    const laterRes = await apiRequest(ctx, '/planner/later', {
      token: ctx.token,
      expectedStatus: 200,
    });
    const foundLater = (laterRes.data?.items || []).some((item: any) => item.taskId === laterTaskId);
    if (!foundLater) {
      throw new Error('Created Later task not found in /planner/later');
    }
    console.log('✓ Later');

    // 5. Create Scheduled ONCE Task
    const onceTaskId = randomUUID();
    ctx.createdTaskIds.push(onceTaskId);
    const onceCreateRes = await apiRequest(ctx, '/tasks', {
      method: 'POST',
      token: ctx.token,
      body: {
        id: onceTaskId,
        title: 'Smoke Test Once Task',
        isImportant: true,
        schedule: {
          type: 'ONCE',
          startDate: today,
          scheduledTime: '10:00:00',
          reminderMinutesBefore: 15,
        },
      },
      expectedStatus: 201,
    });
    const onceScheduleId = onceCreateRes.data?.task?.schedules?.[0]?.id || onceCreateRes.data?.schedules?.[0]?.id;
    if (!onceScheduleId) {
      throw new Error('Created ONCE task missing schedule ID in response');
    }

    // 6. Verify Day View
    const dayRes = await apiRequest(ctx, `/planner/days/${today}`, {
      token: ctx.token,
      expectedStatus: 200,
    });
    const timedItems = dayRes.data?.timed || [];
    const anytimeItems = dayRes.data?.anytime || [];
    const foundDay = [...timedItems, ...anytimeItems].some((item: any) => item.taskId === onceTaskId);
    if (!foundDay) {
      throw new Error(`Created ONCE task not found in /planner/days/${today}`);
    }
    console.log('✓ Day');

    // 7. Create Recurring Task
    const recurringTaskId = randomUUID();
    ctx.createdTaskIds.push(recurringTaskId);
    await apiRequest(ctx, '/tasks', {
      method: 'POST',
      token: ctx.token,
      body: {
        id: recurringTaskId,
        title: 'Smoke Test Recurring Task',
        isImportant: false,
        plannerToday: today,
        schedule: {
          type: 'INTERVAL_DAYS',
          startDate: today,
          intervalDays: 1,
        },
      },
      expectedStatus: 201,
    });
    console.log('✓ Recurrence');

    // 8. Edit / Reschedule a Task
    await apiRequest(ctx, `/tasks/${onceTaskId}`, {
      method: 'PATCH',
      token: ctx.token,
      body: {
        plannerToday: today,
        effectiveDate: today,
        title: 'Smoke Test Once Task (Edited)',
        isImportant: true,
        schedule: {
          type: 'ONCE',
          startDate: today,
          scheduledTime: '11:00:00',
          reminderMinutesBefore: 15,
        },
      },
      expectedStatus: 200,
    });
    console.log('✓ Edit');

    // 9. Complete and Undo Task
    await apiRequest(ctx, `/tasks/${onceTaskId}/complete`, {
      method: 'POST',
      token: ctx.token,
      body: {
        completedDate: today,
        scheduleId: onceScheduleId,
        scheduledDate: today,
      },
      expectedStatus: 200,
    });

    await apiRequest(ctx, `/tasks/${onceTaskId}/undo`, {
      method: 'POST',
      token: ctx.token,
      body: {
        scheduleId: onceScheduleId,
        scheduledDate: today,
      },
      expectedStatus: 200,
    });
    console.log('✓ Complete / Undo');

    // 10. History View Check (complete once task, check history with plannerToday > completedDate, undo completion)
    await apiRequest(ctx, `/tasks/${onceTaskId}/complete`, {
      method: 'POST',
      token: ctx.token,
      body: {
        completedDate: today,
        scheduleId: onceScheduleId,
        scheduledDate: today,
      },
      expectedStatus: 200,
    });

    const tomorrow = formatDate(new Date(Date.now() + 86400000));
    const historyRes = await apiRequest(ctx, `/planner/history?plannerToday=${tomorrow}`, {
      token: ctx.token,
      expectedStatus: 200,
    });
    const groups = historyRes.data?.groups || [];
    const foundHistory = groups.some((g: any) =>
      (g.items || []).some((item: any) => item.taskId === onceTaskId)
    );
    if (!foundHistory) {
      throw new Error('Completed task not found in /planner/history');
    }

    await apiRequest(ctx, `/tasks/${onceTaskId}/undo`, {
      method: 'POST',
      token: ctx.token,
      body: {
        scheduleId: onceScheduleId,
        scheduledDate: today,
      },
      expectedStatus: 200,
    });
    console.log('✓ History');

    // 11. Call /planner/refresh
    const refreshRes = await apiRequest(ctx, '/planner/refresh', {
      token: ctx.token,
      expectedStatus: 200,
    });
    if (
      !Array.isArray(refreshRes.data?.tasks) ||
      !Array.isArray(refreshRes.data?.schedules) ||
      !Array.isArray(refreshRes.data?.completions)
    ) {
      throw new Error('/planner/refresh response shape invalid');
    }
    console.log('✓ Refresh');

    // 12. Stop Recurrence
    await apiRequest(ctx, `/tasks/${recurringTaskId}/stop-recurrence`, {
      method: 'POST',
      token: ctx.token,
      body: {
        plannerToday: today,
      },
      expectedStatus: 200,
    });
    console.log('✓ Stop recurrence');

    // 13. Delete Created Tasks
    for (const taskId of ctx.createdTaskIds) {
      await apiRequest(ctx, `/tasks/${taskId}`, {
        method: 'DELETE',
        token: ctx.token,
        expectedStatus: 200,
      });
    }
    console.log('✓ Delete');

    // 14. Logout
    await apiRequest(ctx, '/auth/logout', {
      method: 'POST',
      token: ctx.token,
      expectedStatus: 204,
    });
    console.log('✓ Logout');

    // 15. Prove Logged-Out Token Fails Authentication
    await apiRequest(ctx, '/auth/me', {
      token: ctx.token,
      expectedStatus: 401,
    });
    console.log('✓ Token invalidated');

    console.log('\nBACKEND SMOKE TEST PASSED\n');
  } catch (error: any) {
    console.error(`\n✗ Smoke test failed: ${error.message || error}`);
    // Best-effort cleanup of created tasks if token was acquired
    if (ctx.token && ctx.createdTaskIds.length > 0) {
      for (const taskId of ctx.createdTaskIds) {
        try {
          await apiRequest(ctx, `/tasks/${taskId}`, {
            method: 'DELETE',
            token: ctx.token,
            expectedStatus: 200,
          });
        } catch {
          // ignore cleanup failures
        }
      }
    }
    process.exitCode = 1;
  }
}

if (require.main === module) {
  runSmokeTest();
}
