import * as readline from 'node:readline';
import { Writable } from 'node:stream';
import { createUserAccount } from '../src/auth/user-creation';
import { closePool } from '../src/db/pool';

function promptText(query: string): Promise<string> {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  return new Promise((resolve) => {
    rl.question(query, (answer) => {
      rl.close();
      resolve(answer);
    });
  });
}

function promptPassword(query: string): Promise<string> {
  return new Promise((resolve) => {
    let isMuted = false;
    const mutableStdout = new Writable({
      write(chunk, encoding, callback) {
        if (!isMuted) {
          process.stdout.write(chunk, encoding);
        }
        callback();
      },
    });

    const rl = readline.createInterface({
      input: process.stdin,
      output: mutableStdout,
      terminal: true,
    });

    rl.question(query, (answer) => {
      rl.close();
      console.log(); // Print newline after masked input
      resolve(answer);
    });
    isMuted = true;
  });
}

async function run() {
  console.log('=== Day by Day: User Account Creation ===\n');

  try {
    const email = await promptText('Email: ');
    const firstName = await promptText('First Name: ');
    const lastName = await promptText('Last Name: ');
    const nicknameInput = await promptText('Nickname (optional, press enter to skip): ');
    const password = await promptPassword('Password (min 15 chars, input hidden): ');

    const user = await createUserAccount({
      email,
      firstName,
      lastName,
      nickname: nicknameInput,
      password,
    });

    console.log('\nUser account created successfully:');
    console.log(`  ID:         ${user.id}`);
    console.log(`  Email:      ${user.email}`);
    console.log(`  First Name: ${user.firstName}`);
    console.log(`  Last Name:  ${user.lastName}`);
    console.log(`  Nickname:   ${user.nickname ?? '(none)'}`);
  } catch (error: any) {
    console.error('\nUser creation failed:', error.message || error);
    process.exitCode = 1;
  } finally {
    await closePool();
  }
}

run();
