import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import StartVotePanel from "./StartVotePanel.vue";

describe("StartVotePanel", () => {
  it("counts only human players and votes", () => {
    const wrapper = mount(StartVotePanel, {
      props: {
        selfId: "host-1",
        room: {
          id: "room-1",
          status: "waiting",
          players: ["host-1", "computer:room-1:bot"],
          startVotes: ["host-1", "computer:room-1:bot"],
          settings: { startVoteThresholdMode: "auto" },
        },
      },
    });

    expect(wrapper.text()).toContain("1/1");
    expect(wrapper.text()).toContain("1 真人");
    expect(wrapper.text()).toContain("1 人机");
  });
});
