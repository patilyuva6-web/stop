const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const STUDENT_UID = "student_alice_1";
const TEACHER_UID = "teacher_bob_1";
const OTHER_UID = "stranger_charlie_1";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

const pastDate = () => new Date(Date.now() - 2000);
const futureDate = () => new Date(Date.now() + 60000);

test("Unauthenticated requests are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(STUDENT_UID).get());
  await assertFails(unauthDb.collection("students").where("ownerUid", "==", STUDENT_UID).get());
  await assertFails(unauthDb.collection("classes").where("active", "==", true).get());
  await assertFails(unauthDb.collection("activity").where("ownerUid", "==", STUDENT_UID).get());
  await assertFails(unauthDb.collection("breakRequests").where("ownerUid", "==", STUDENT_UID).get());
});

test("UserProfile: owner can create/update/read; cross-user and ghost fields fail", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      role: "student",
      displayName: "Aarav Sharma",
      usn: "1MS22CS001",
      teacherId: "",
      division: "A",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update allowed fields
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).update({
      division: "B",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field on create
  await assertFails(
    otherDb.collection("users").doc(OTHER_UID).set({
      userId: OTHER_UID,
      role: "student",
      displayName: "Charlie",
      usn: "1MS22CS099",
      teacherId: "",
      division: "A",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject cross-user read & write
  await assertFails(otherDb.collection("users").doc(STUDENT_UID).get());
  await assertFails(otherDb.collection("users").doc(STUDENT_UID).update({ displayName: "Hacked" }));
});

test("StudentRecord: owner & watcher teacher read; stranger denied; unfiltered list denied", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    studentDb.collection("students").doc("1MS22CS001").set({
      usn: "1MS22CS001",
      ownerUid: STUDENT_UID,
      name: "Aarav Sharma",
      division: "A",
      inClassMode: true,
      activeClassId: "cls_1",
      activeSubject: "Operating Systems",
      breakUntilEpochMs: 0,
      watcherUids: [STUDENT_UID, TEACHER_UID],
      lastLogin: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Matching Kotlin queries succeed
  await assertSucceeds(studentDb.collection("students").where("ownerUid", "==", STUDENT_UID).get());
  await assertSucceeds(teacherDb.collection("students").where("watcherUids", "array-contains", TEACHER_UID).get());

  // Unfiltered query or stranger access denied
  await assertFails(otherDb.collection("students").get());
  await assertFails(otherDb.collection("students").doc("1MS22CS001").get());
  await assertFails(teacherDb.collection("students").doc("1MS22CS001").update({ inClassMode: false }));

  // Future timestamp rejected
  await assertFails(
    studentDb.collection("students").doc("1MS22CS001").update({
      inClassMode: false,
      updatedAt: futureDate(),
    })
  );
});

test("ClassSchedule: teacher CRUD + student division query; cross-teacher edit denied", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    teacherDb.collection("classes").doc("cls_mon_0900_A").set({
      teacherUid: TEACHER_UID,
      teacherId: "T001",
      teacherName: "Prof. Rao",
      subject: "Operating Systems",
      division: "A",
      day: "MON",
      start: "09:00",
      end: "10:00",
      room: "LH-204",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Teacher updates own class
  await assertSucceeds(
    teacherDb.collection("classes").doc("cls_mon_0900_A").update({
      room: "LH-301",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Student queries active classes for division A
  await assertSucceeds(
    studentDb.collection("classes")
      .where("active", "==", true)
      .where("division", "==", "A")
      .get()
  );

  // Teacher queries own classes
  await assertSucceeds(
    teacherDb.collection("classes").where("teacherUid", "==", TEACHER_UID).get()
  );

  // Invalid time order (start >= end) rejected
  await assertFails(
    teacherDb.collection("classes").doc("cls_bad").set({
      teacherUid: TEACHER_UID,
      teacherId: "T001",
      teacherName: "Prof. Rao",
      subject: "DBMS",
      division: "A",
      day: "MON",
      start: "11:00",
      end: "10:00",
      room: "LH-101",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Another user cannot edit/delete teacher's class
  await assertFails(otherDb.collection("classes").doc("cls_mon_0900_A").update({ subject: "Hijacked" }));
  await assertFails(otherDb.collection("classes").doc("cls_mon_0900_A").delete());
});

test("ClassActivity: student writes live telemetry; teacher observes; stranger denied", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    studentDb.collection("activity").doc("20261122_cls_mon_0900_A_1MS22CS001").set({
      dateKey: "20261122",
      classId: "cls_mon_0900_A",
      usn: "1MS22CS001",
      studentName: "Aarav Sharma",
      division: "A",
      subject: "Operating Systems",
      ownerUid: STUDENT_UID,
      teacherUid: TEACHER_UID,
      active: true,
      breakActive: false,
      startedAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    teacherDb.collection("activity")
      .where("teacherUid", "==", TEACHER_UID)
      .where("dateKey", "==", "20261122")
      .get()
  );

  await assertSucceeds(
    studentDb.collection("activity").doc("20261122_cls_mon_0900_A_1MS22CS001").update({
      active: false,
      breakActive: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Stranger cannot read or tamper
  await assertFails(otherDb.collection("activity").doc("20261122_cls_mon_0900_A_1MS22CS001").get());
  await assertFails(otherDb.collection("activity").where("teacherUid", "==", TEACHER_UID).get());
});

test("BreakRequest: student creates PENDING; teacher approves once with 4-digit code; terminal state locked", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    studentDb.collection("breakRequests").doc("req_1").set({
      ownerUid: STUDENT_UID,
      usn: "1MS22CS001",
      studentName: "Aarav Sharma",
      division: "A",
      classId: "cls_mon_0900_A",
      subject: "Operating Systems",
      teacherUid: TEACHER_UID,
      reason: "Urgent phone call from parents",
      status: "PENDING",
      breakCode: "",
      durationMinutes: 10,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student cannot self-approve
  await assertFails(
    studentDb.collection("breakRequests").doc("req_1").update({
      status: "APPROVED",
      breakCode: "1234",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Teacher approves with 4-digit break code
  await assertSucceeds(
    teacherDb.collection("breakRequests").doc("req_1").update({
      status: "APPROVED",
      breakCode: "4829",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Terminal state locking: cannot re-modify once APPROVED
  await assertFails(
    teacherDb.collection("breakRequests").doc("req_1").update({
      status: "DENIED",
      breakCode: "",
      updatedAt: new Date(Date.now() - 300),
    })
  );
});
